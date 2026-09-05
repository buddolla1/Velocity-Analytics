package com.jira.analytics.dto;

public record EmployeePrMetrics(
        String employee,
        long prsMerged,
        long jiraMatchedPrs,
        Double averageCycleTimeDays,
        Double medianCycleTimeDays,
        long repositories
) {
}
