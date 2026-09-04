package com.jira.analytics.dto;

public record DashboardSummary(
        long totalIssues,
        long storiesCompleted,
        double storyPointsCompleted,
        long defectsClosed,
        long activeContributors,
        Double averageCycleTimeDays
) {
}
