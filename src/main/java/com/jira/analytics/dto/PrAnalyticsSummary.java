package com.jira.analytics.dto;

public record PrAnalyticsSummary(
        int totalPrs,
        int mergedPrs,
        int jiraMatchedPrs,
        int unmatchedPrs,
        double jiraMatchPercentage,
        Double averageCycleTimeDays,
        Double medianCycleTimeDays,
        Double averageFirstCommitToMergeDays,
        Double averageCreatedToMergeDays,
        int activePrAuthors
) {
}
