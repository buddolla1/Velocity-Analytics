package com.jira.analytics.dto;

public record EmployeeMetrics(
        String employee,
        long storiesCompleted,
        double storyPointsCompleted,
        long defectsCompleted,
        long sprintCount,
        Double averageCycleTimeDays
) {
}
