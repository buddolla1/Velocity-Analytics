package com.jira.analytics.dto;

public record SprintMetrics(
        String sprint,
        long totalStories,
        long completedStories,
        double storyPoints,
        double completionPercentage,
        long defects,
        long contributors
) {
}
