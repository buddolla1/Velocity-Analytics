package com.jira.analytics.dto;

public record JiraSyncRequest(
        String username,
        String apiToken,
        String projectKey,
        String endDate
) {
}
