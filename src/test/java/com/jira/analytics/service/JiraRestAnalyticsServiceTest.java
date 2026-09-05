package com.jira.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jira.analytics.config.JiraProperties;
import com.jira.analytics.dto.JiraSyncRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JiraRestAnalyticsServiceTest {

    @Test
    void syncRejectsMultipleUsernames() {
        JiraRestAnalyticsService service = buildService();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sync(new JiraSyncRequest("user-one@example.com, user-two@example.com", "token", "OPS", "2026-09-05"))
        );

        assertEquals("jira.username must contain a single value.", exception.getMessage());
    }

    @Test
    void syncRejectsMultipleApiTokens() {
        JiraRestAnalyticsService service = buildService();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sync(new JiraSyncRequest("user@example.com", "token-one, token-two", "OPS", "2026-09-05"))
        );

        assertEquals("jira.api-token must contain a single value.", exception.getMessage());
    }

    @Test
    void composeJqlUsesStartDateThroughCurrentDate() throws Exception {
        JiraRestAnalyticsService service = buildService();
        Method composeJql = JiraRestAnalyticsService.class.getDeclaredMethod(
                "composeJql",
                String.class,
                String.class,
                String.class
        );
        composeJql.setAccessible(true);

        String jql = (String) composeJql.invoke(service, "project = OPS", "2026-09-01", "2026-09-05");

        assertEquals("project = OPS AND updated >= \"2026-09-01\" AND updated <= \"2026-09-05\" ORDER BY updated ASC, id ASC", jql);
    }

    private JiraRestAnalyticsService buildService() {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl("https://example.atlassian.net");
        properties.setProjectKey("OPS");
        return new JiraRestAnalyticsService(properties, new JiraIssueRecordRepository(null), new ObjectMapper());
    }
}
