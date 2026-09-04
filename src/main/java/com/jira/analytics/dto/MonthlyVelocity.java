package com.jira.analytics.dto;

public record MonthlyVelocity(
        int monthNumber,
        String monthName,
        long storiesCompleted,
        double storyPointsCompleted,
        long defectsCompleted
) {
}
