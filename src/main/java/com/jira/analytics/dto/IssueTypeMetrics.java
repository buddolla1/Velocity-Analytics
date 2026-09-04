package com.jira.analytics.dto;

public record IssueTypeMetrics(
        String issueType,
        long count
) {
}
