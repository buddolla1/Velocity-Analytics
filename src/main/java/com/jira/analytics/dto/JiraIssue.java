package com.jira.analytics.dto;

public record JiraIssue(
        String issueType,
        String issueKey,
        String issueId,
        String status,
        String projectKey,
        String projectName,
        String summary,
        Double storyPoints,
        String sprint,
        String assignee,
        String sso,
        String resolved,
        Integer monthNumber,
        String monthName,
        String toDoToInProgress,
        String validation,
        String done,
        Double cycleTimeDays
) {
}
