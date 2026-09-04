package com.jira.analytics.dto;

import java.util.List;

public record DashboardResponse(
        DashboardSummary summary,
        List<MonthlyVelocity> monthlyVelocity,
        List<SprintMetrics> sprintMetrics,
        List<EmployeeMetrics> employeeMetrics,
        List<IssueTypeMetrics> issueTypeMetrics,
        List<JiraIssue> issues
) {
}
