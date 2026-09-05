package com.jira.analytics.dto;

public record CycleStartSourceMetric(
        String source,
        long count,
        double percentage
) {
}
