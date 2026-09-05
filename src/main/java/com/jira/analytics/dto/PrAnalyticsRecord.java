package com.jira.analytics.dto;

public record PrAnalyticsRecord(
        String prId,
        String repositoryName,
        String projectName,
        String authorName,
        String authorUsername,
        String state,
        String prTitle,
        String prDescription,
        String sourceBranch,
        String destinationBranch,
        String jiraKey,
        boolean jiraMatch,
        String jiraAssignee,
        String jiraStatus,
        String jiraProjectName,
        String jiraSprint,
        Double jiraStoryPoints,
        String jiraInProgressAt,
        String prCreatedAt,
        String firstCommitAt,
        String cycleStart,
        String cycleStartSource,
        String prMergedAt,
        Double cycleTimeHours,
        Double cycleTimeDays
) {
}
