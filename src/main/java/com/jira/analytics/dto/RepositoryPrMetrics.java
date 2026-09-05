package com.jira.analytics.dto;

public record RepositoryPrMetrics(
        String repository,
        long prsMerged,
        Double averageCycleTimeDays,
        double jiraMatchPercentage
) {
}
