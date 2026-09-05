package com.jira.analytics.dto;

public record MonthlyPrMetrics(
        int monthNumber,
        String monthName,
        long prsMerged,
        Double averageCycleTimeDays,
        long matchedPrs,
        long unmatchedPrs
) {
}
