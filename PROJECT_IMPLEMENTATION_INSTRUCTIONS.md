# PROJECT IMPLEMENTATION INSTRUCTIONS

## Project Goal

Build and update the existing application with:

- Jira REST integration
- Optimized bulk changelog retrieval
- Jira status-transition extraction
- Spring JDBC persistence
- H2 database for now
- Easy MySQL migration later
- No duplicate Jira records
- Jira analytics APIs
- Existing React analytics dashboard integration
- Existing Jira + Bitbucket PR analytics integration

Do NOT recreate the project from scratch.

Use the existing Spring Boot and React codebase.

---

# PHASE 1 - JIRA REST INTEGRATION

## 1. Jira Search

Use Jira Cloud REST API.

Do NOT fetch changelog individually for every issue.

Use the Jira search API to retrieve only required Jira fields.

Required Jira fields:

- Issue Type
- Issue Key
- Issue ID
- Status
- Project Key
- Project Name
- Summary
- Story Points
- Sprint
- Assignee
- SSO if available
- Resolution Date
- Updated Date

Use pagination until all Jira issues are collected.

The Jira project may contain 5,000+ records.

Do not load unnecessary Jira fields.

Do not fetch:

- comments
- attachments
- worklogs
- full changelog inside issue search

---

# PHASE 2 - BULK CHANGELOG

## 2. Bulk Changelog Retrieval

After Jira search returns issue IDs/keys:

Split issues into batches of maximum 1000.

Use:

POST /rest/api/3/changelog/bulkfetch

Fetch changelog only for the Jira status field.

Process all changelog pages if pagination exists.

Do NOT execute:

GET /issue/{issueKey}/changelog

for thousands of issues.

---

# PHASE 3 - STATUS TRANSITION EXTRACTION

## 3. Required Jira Transitions

Extract timestamps for:

To Do -> In Progress

In Progress -> Validation

Validation -> Done

Store normalized transition values as:

toDoToInProgressAt

inProgressToValidationAt

validationToDoneAt

Use OffsetDateTime.

---

# 4. Jira Cycle Time

For Jira-only analytics calculate:

jiraCycleTimeDays

from:

To Do -> In Progress

to:

Validation -> Done

If required timestamps are missing:

jiraCycleTimeDays = null

Do NOT use zero for missing cycle time.

---

# PHASE 4 - JIRA NORMALIZED MODEL

## 5. JiraIssueRecord

Create or update:

com.syf.jirametrics.model.JiraIssueRecord

Fields:

Long id

String issueId

String issueKey

String issueType

String status

String projectKey

String projectName

String summary

Double storyPoints

String sprint

String assignee

String sso

OffsetDateTime resolvedAt

Integer monthNumber

String monthName

OffsetDateTime toDoToInProgressAt

OffsetDateTime inProgressToValidationAt

OffsetDateTime validationToDoneAt

Double jiraCycleTimeDays

OffsetDateTime jiraUpdatedAt

OffsetDateTime lastSyncedAt

---

# PHASE 5 - DATABASE

## 6. Use Spring JDBC

Use:

spring-boot-starter-jdbc

JdbcTemplate

Do NOT use:

JPA

Hibernate

Spring Data JPA

---

# 7. H2 Database

Use H2 for now.

Use file-based H2.

Do NOT use in-memory H2.

Example:

spring:
datasource:
url: jdbc:h2:file:./data/jira-analytics-db;AUTO_SERVER=TRUE
driver-class-name: org.h2.Driver
username: sa
password:

sql:
init:
mode: always

h2:
console:
enabled: true
path: /h2-console

Data must survive application restart.

---

# 8. Future MySQL Migration

Design JDBC repositories so H2 can later be changed to MySQL.

Avoid H2-specific MERGE logic.

Prefer:

SELECT

INSERT

UPDATE

Use standard SQL where possible.

Later datasource should be able to change to:

jdbc:mysql://localhost:3306/jira_analytics

without changing business logic.

---

# PHASE 6 - DATABASE SCHEMA

## 9. schema.sql

Create:

src/main/resources/schema.sql

Create table:

jira_issue

Columns:

id BIGINT generated identity primary key

issue_id VARCHAR(50) NOT NULL

issue_key VARCHAR(100) NOT NULL

issue_type VARCHAR(100)

status VARCHAR(100)

project_key VARCHAR(100)

project_name VARCHAR(255)

summary VARCHAR(2000)

story_points DOUBLE

sprint VARCHAR(500)

assignee VARCHAR(255)

sso VARCHAR(255)

resolved_at TIMESTAMP WITH TIME ZONE

month_number INT

month_name VARCHAR(50)

to_do_to_in_progress_at TIMESTAMP WITH TIME ZONE

in_progress_to_validation_at TIMESTAMP WITH TIME ZONE

validation_to_done_at TIMESTAMP WITH TIME ZONE

jira_cycle_time_days DOUBLE

jira_updated_at TIMESTAMP WITH TIME ZONE

last_synced_at TIMESTAMP WITH TIME ZONE

Add unique constraints:

UNIQUE(issue_id)

UNIQUE(issue_key)

Add indexes:

project_key

assignee

resolved_at

jira_updated_at

---

# 10. Sync Status Table

Create:

jira_sync_status

Columns:

sync_name VARCHAR(100) PRIMARY KEY

last_sync_at TIMESTAMP WITH TIME ZONE

last_status VARCHAR(50)

records_inserted INT

records_updated INT

error_message VARCHAR(2000)

Use:

sync_name = 'JIRA'

---

# PHASE 7 - JDBC REPOSITORY

## 11. JiraIssueJdbcRepository

Create:

com.syf.jirametrics.repository.JiraIssueJdbcRepository

Use JdbcTemplate.

Required methods:

findByIssueId(String issueId)

findByIssueKey(String issueKey)

findAll()

findAllIssueIds()

batchInsert(List<JiraIssueRecord> issues)

batchUpdate(List<JiraIssueRecord> issues)

count()

Do not perform thousands of individual insert statements.

Use batchUpdate.

Recommended batch size:

500

---

# PHASE 8 - NO DUPLICATES

## 12. Duplicate Prevention

Do NOT delete the table and reinsert all records.

Do NOT blindly INSERT every refresh.

Use:

issueId

as the main stable Jira identity.

Keep:

issueKey

unique as secondary protection.

Refresh behavior:

New issue:
INSERT

Existing issue:
UPDATE

Never create duplicate rows.

---

# 13. Optimized Persistence

For 5,000 Jira records:

1. Fetch existing issue IDs once.
2. Store in HashMap or HashSet.
3. Split incoming records into:
    - inserts
    - updates
4. batchInsert inserts
5. batchUpdate updates

Do not execute:

findByIssueId()

for every record if batch persistence can avoid it.

---

# PHASE 9 - SYNC SERVICE

## 14. JiraSyncService

Create or update:

com.syf.jirametrics.service.JiraSyncService

Responsibilities:

1. Run Jira JQL search
2. Retrieve all issue pages
3. Collect Jira issue IDs/keys
4. Fetch changelog in batches of 1000
5. Extract required transitions
6. Normalize JiraIssueRecord
7. Calculate Jira cycle time
8. Batch insert/update H2
9. Update jira_sync_status

Return:

recordsFetched

recordsInserted

recordsUpdated

lastSyncTime

status

---

# 15. Initial Sync

Initial sync may use configured JQL such as:

project in (...)

AND resolved >= "2026-01-01"

Do not hardcode project names inside repository code.

Keep Jira JQL configurable.

---

# 16. Incremental Sync

Support future incremental sync.

Use:

last_sync_at

from jira_sync_status.

Subsequent Jira refresh may use:

updated >= lastSyncTime

Only changed issues need to be retrieved and updated.

React should not care whether the backend performed:

full sync

or:

incremental sync

---

# PHASE 10 - CONTROLLERS

## 17. Jira Refresh Endpoint

Create:

POST /api/jira/refresh

Behavior:

Run JiraSyncService.

Return:

{
"status": "SUCCESS",
"recordsFetched": 250,
"recordsInserted": 15,
"recordsUpdated": 235,
"lastSyncTime": "..."
}

If error occurs:

return useful error response.

Do not crash application.

---

# 18. Jira Analytics Data Endpoint

Create:

GET /api/jira/analytics-data

Read normalized Jira data from H2.

Do NOT re-call Jira when this endpoint is invoked.

Return:

{
"issues": [...],
"totalRecords": 5000,
"lastSyncTime": "..."
}

---

# PHASE 11 - REACT JIRA DASHBOARD

## 19. Existing React Application

Do NOT recreate the React project.

Keep existing pages:

Overview

Sprints

Team

Issues

Cycle Time / PR Analytics

---

# 20. Remove Jira Excel Dependency

For the main Jira dashboard:

Do not require Jira Excel upload.

Use backend data from:

GET /api/jira/analytics-data

Provide button:

Refresh Jira Data

When clicked:

POST /api/jira/refresh

Then reload:

GET /api/jira/analytics-data

---

# 21. Jira React Data Model

Use:

export interface JiraIssue {

    issueType: string;

    issueKey: string;

    issueId?: string;

    status: string;

    projectKey: string;

    projectName: string;

    summary: string;

    storyPoints?: number | null;

    sprint?: string | null;

    assignee?: string | null;

    sso?: string | null;

    resolvedAt?: string | null;

    monthNumber?: number | null;

    monthName?: string | null;

    toDoToInProgressAt?: string | null;

    inProgressToValidationAt?: string | null;

    validationToDoneAt?: string | null;

    jiraCycleTimeDays?: number | null;

    jiraUpdatedAt?: string | null;
}

---

# 22. API Service

Create or update:

src/services/jiraApi.ts

Functions:

getJiraAnalyticsData()

refreshJiraData()

Use relative API URLs:

/api/jira/analytics-data

/api/jira/refresh

---

# 23. Jira Dashboard Loading

On application load:

GET /api/jira/analytics-data

Store results in React state.

Do not re-fetch from Jira for every filter.

---

# 24. Jira Filters

Keep client-side filters:

Project

Month

Sprint

Assignee

Issue Type

Status

Use useMemo.

---

# 25. Jira Analytics

Keep current analytics.

Required:

Stories Completed

Story Points Completed

Average Jira Cycle Time

Defects Closed

Active Contributors

Monthly Velocity

Monthly Story Point Velocity

Sprint Throughput

Sprint Completion %

Team Analytics

Issue Type Analytics

Project Analytics

---

# 26. Completed Issue Definition

Completed means:

status is:

Done

Closed

Resolved

OR:

resolvedAt is populated

---

# 27. Monthly Velocity

Calculate:

count of completed Stories

grouped by month.

Sort using:

monthNumber

not alphabetically.

---

# 28. Story Point Velocity

Calculate:

SUM storyPoints

for completed Stories

grouped by month.

---

# 29. Sprint Analytics

For each Sprint calculate:

Total Stories

Completed Stories

Completed Story Points

Defects

Contributors

Completion %

---

# 30. Employee Analytics

Group by Assignee.

Calculate:

Stories Completed

Story Points Completed

Defects Completed

Sprint Count

Average Jira Cycle Time

Do NOT rank employees.

Exclude null cycle time.

Do NOT treat null as zero.

---

# 31. Jira Cycle Time Section

On Overview show:

Average Jira Cycle Time

Median Jira Cycle Time

Monthly Jira Cycle Time

Employee Average Jira Cycle Time

Use backend field:

jiraCycleTimeDays

React must not recalculate raw changelog transitions.

---

# 32. Issues Table

Show:

Issue Key

Issue Type

Project

Summary

Assignee

Sprint

Status

Story Points

Resolved

To Do -> In Progress

In Progress -> Validation

Validation -> Done

Jira Cycle Time Days

Use horizontal scrolling if required.

---

# 33. Large Data Handling

The Jira dataset can contain 5,000+ records.

Do not render all rows at once.

Use pagination.

Default page size:

50

Allow:

25

50

100

Use useMemo for:

filtered records

analytics

pagination

dropdown values

---

# 34. Jira Search

Add issue search on Issues page.

Search:

Issue Key

Summary

Assignee

Search must work together with filters.

---

# PHASE 12 - JIRA + BITBUCKET PR ANALYTICS

## 35. Keep PR Analytics Separate

Do not mix Jira-only cycle time with PR cycle time.

Keep page:

Cycle Time / PR Analytics

as a separate module.

---

# 36. Bitbucket Is Primary for PR Analytics

For combined Jira + Bitbucket analytics:

Bitbucket records are primary.

Every Bitbucket PR should remain in the result even if no Jira match exists.

Match:

Bitbucket Jira Key

to:

Jira issueKey

---

# 37. Bitbucket Data Center

This environment is Bitbucket Data Center / Server.

Use endpoints:

/rest/api/1.0/projects/{project}/repos/{repo}/pull-requests/{id}

/rest/api/1.0/projects/{project}/repos/{repo}/pull-requests/{id}/commits?limit=100

/rest/api/1.0/projects/{project}/repos/{repo}/pull-requests/{id}/activities?limit=100

Use:

limit

not:

pagelen

Use:

/activities

not:

/activity

---

# 38. Bitbucket First Commit

For PR commits:

First Commit At =
minimum commit.committerTimestamp

Use:

committerTimestamp

not generic date.

Convert epoch millis using:

Instant.ofEpochMilli(...)

---

# 39. PR Created At

Use:

pullRequest.createdDate

Convert epoch milliseconds.

---

# 40. PR Merged At

Preferred source:

PR activities

Find:

action == "MERGED"

Use activity:

createdDate

Fallback:

PR closedDate

only when PR state is MERGED.

---

# 41. Jira Key Extraction

Prefer Jira key from commit property:

properties["jira-key"]

If unavailable, fallback to regex extraction from:

Commit message

PR title

PR description

Source branch

Typical regex:

([A-Z][A-Z0-9]+-\d+)

---

# 42. PR Cycle Start Logic

If Jira key matches Jira record:

Cycle Start =
LEAST(
BB First Commit At,
BB PR Created At,
Jira To Do -> In Progress
)

If Jira does not match:

Cycle Start =
LEAST(
BB First Commit At,
BB PR Created At
)

Ignore null values when finding earliest available timestamp.

---

# 43. PR Cycle Time

Cycle Time =

BB PR Merged At - Cycle Start

Do NOT use Jira Done date in PR cycle time.

---

# 44. Cycle Start Source

Return one of:

BB FIRST COMMIT

BB PR CREATED

JIRA IN PROGRESS

---

# 45. PR Analytics Person

For PR analytics:

If Jira match exists and Jira Assignee is populated:

employee = Jira Assignee

Otherwise:

employee = Bitbucket Author Name

---

# 46. PR Analytics Calculations

Only MERGED PRs count in completed cycle-time analytics.

OPEN PRs may appear in detail table.

Do not include OPEN PRs in:

average cycle time

median cycle time

weekly cycle time

monthly cycle time

---

# 47. PR Analytics Summary

Calculate:

Total PRs

Merged PRs

Jira Matched PRs

Unmatched PRs

Jira Match %

Average Cycle Time Days

Median Cycle Time Days

Average First Commit to Merge Days

Average Created to Merge Days

Active PR Authors

---

# 48. PR Analytics Charts

Include:

Monthly PR Throughput

Monthly Average Cycle Time

Employee PR Metrics

Repository Metrics

Cycle Start Source Breakdown

Weekly Cycle Time Table

---

# 49. Weekly Cycle Time

Use fixed 7-day windows starting:

Week 1:
2026-01-01 to 2026-01-07

Week 2:
2026-01-08 to 2026-01-14

Continue every 7 days.

Assign PR to week using:

PR Merged At

Table format:

Week #
Week Start
Week End
Employee A
Employee B
Employee C
...

Cell value:

average PR cycle time days for that employee in that week.

If no PR exists:

display —

Do not display zero.

Do not include missing values in averages.

---

# 50. PR Detailed Table

Display:

PR ID

Repository

Project

Author

PR Title

Jira Key

Jira Match

Jira Assignee

Sprint

Story Points

PR Created At

First Commit At

Jira In Progress At

Cycle Start

Cycle Start Source

PR Merged At

Cycle Time Days

---

# 51. PR Filters

Support:

Project

Repository

Month

Employee

Jira Match

Sprint

Cycle Start Source

---

# PHASE 13 - CODE QUALITY

## 52. Separate Responsibilities

Do not create one giant service.

Keep separate classes for:

Jira REST client

Jira issue search

Bulk changelog fetch

Transition extraction

Jira persistence

Jira sync

Jira controller

Bitbucket REST client

PR analytics

JDBC repository

---

# 53. Error Handling

Handle:

Jira REST errors

Bitbucket REST errors

database errors

null dates

missing changelog

missing Jira key

unmatched Jira record

pagination

rate limits

Do not allow one malformed record to crash the entire batch unless necessary.

---

# 54. Logging

Log:

sync start

JQL page count

issues fetched

changelog batches

records inserted

records updated

sync completion

errors

Do not log:

passwords

API tokens

authorization headers

---

# 55. Build Verification

After backend changes:

Run the Spring Boot build.

Fix all compile errors.

Start application.

Verify H2 schema initializes.

Verify refresh endpoint.

Verify analytics-data endpoint.

Verify duplicate Jira rows are not created after running refresh twice.

Expected behavior:

First refresh:

5000 inserted
0 updated

Second refresh:

0 inserted
5000 updated

or only changed records updated if incremental sync is enabled.

Total row count must remain 5000.

---

# PHASE 14 - IMPLEMENTATION ORDER

Implement in this exact order.

## Step 1

Implement Jira REST search only.

Verify Jira issues are retrieved.

## Step 2

Implement bulk changelog fetch.

Verify status changelog is retrieved.

## Step 3

Implement transition extraction.

Verify:

To Do -> In Progress

In Progress -> Validation

Validation -> Done

## Step 4

Implement JiraIssueRecord normalization.

## Step 5

Implement H2 schema and JdbcTemplate repository.

## Step 6

Implement batch insert/update with no duplicates.

## Step 7

Implement JiraSyncService.

## Step 8

Implement:

POST /api/jira/refresh

## Step 9

Implement:

GET /api/jira/analytics-data

## Step 10

Run Jira refresh twice.

Verify row count does not increase on second refresh.

## Step 11

Update existing React Jira dashboard to read database-backed API.

## Step 12

Update existing PR Analytics page.

## Step 13

Build both backend and frontend.

Fix all compilation and TypeScript errors.

---

# IMPORTANT IMPLEMENTATION RULES

Do not recreate existing working code unnecessarily.

Do not introduce JPA.

Use JdbcTemplate.

Use batch operations for 5,000+ records.

Use H2 file database.

Keep SQL portable for future MySQL migration.

Never duplicate Jira records.

Use Jira issueId as stable identity.

Keep issueKey unique.

Do not delete all records during refresh.

Do not execute thousands of individual Jira changelog requests.

Use Jira bulk changelog API.

Keep Jira-only analytics separate from Jira + Bitbucket PR analytics.

Do not use Jira Done date for PR cycle time.

Do not treat missing cycle time as zero.

Do not rank employees.

Do not remove existing React pages.

Do not recreate the React application.

---

# FIRST CHATGPT TASK IN INTELLIJ

Read this complete instruction file.

Inspect the existing project before changing code.

Implement only:

PHASE 1 through PHASE 8.

That means:

- Jira REST search
- bulk changelog
- status transition extraction
- normalized Jira model
- H2
- Spring JDBC
- no-duplicate persistence
- optimized batch persistence

Do NOT modify the React application yet.

Do NOT implement Bitbucket changes yet.

Reuse existing project classes wherever appropriate.

After implementation:

1. build the backend
2. fix compile errors
3. show all files created or modified
4. explain how to test the Jira refresh
5. verify that running the same Jira sync twice does not create duplicates