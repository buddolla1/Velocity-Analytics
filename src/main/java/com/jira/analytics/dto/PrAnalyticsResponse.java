package com.jira.analytics.dto;

import java.util.List;

public record PrAnalyticsResponse(
        PrAnalyticsSummary summary,
        List<MonthlyPrMetrics> monthlyMetrics,
        List<EmployeePrMetrics> employeeMetrics,
        List<RepositoryPrMetrics> repositoryMetrics,
        List<CycleStartSourceMetric> cycleStartSources,
        List<PrAnalyticsRecord> records
) {
}
