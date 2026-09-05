package com.syf.jirametrics.model;

import java.time.OffsetDateTime;

public record JiraIssueRecord(
        Long id,
        String issueId,
        String issueKey,
        String issueType,
        String status,
        String projectKey,
        String projectName,
        String summary,
        Double storyPoints,
        String sprint,
        String assignee,
        String sso,
        OffsetDateTime resolvedAt,
        Integer monthNumber,
        String monthName,
        OffsetDateTime toDoToInProgressAt,
        OffsetDateTime inProgressToValidationAt,
        OffsetDateTime validationToDoneAt,
        Double jiraCycleTimeDays,
        OffsetDateTime jiraUpdatedAt,
        OffsetDateTime lastSyncedAt
) {
}
