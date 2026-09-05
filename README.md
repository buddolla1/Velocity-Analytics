# Velocity Analytics

Spring Boot service for syncing Jira Cloud data into a local H2 database and serving analytics to the React dashboard.

## Run

```bash
./gradlew build
./gradlew bootRun
```

## Jira Sync Configuration

Set these environment variables before syncing Jira data:

- `JIRA_BASE_URL`
- `JIRA_USERNAME` single value
- `JIRA_API_TOKEN` single value
- The sync request sends a single `startDate` value in `yyyy-MM-dd` format and the app syncs through the current date
- `JIRA_PROJECT_KEY` or `JIRA_JQL`
- `JIRA_STORY_POINTS_FIELD` optional, defaults to `customfield_10016`
- `JIRA_SPRINT_FIELD` optional, defaults to `customfield_10020`
- `JIRA_SSO_FIELD` optional

The app stores Jira records in a file-based H2 database at `./data/jira-analytics-db`.

## API

- `GET /api/jira/dashboard` returns the current stored Jira analytics
- `POST /api/jira/sync` fetches Jira Cloud issues and changelogs, persists them, and returns the refreshed dashboard
- `POST /api/jira/upload` keeps the legacy Excel upload path for the existing spreadsheet workflow

## Frontend

The React dashboard is built into the Spring Boot app during `./gradlew build` and served from the same process on port `8082`.
