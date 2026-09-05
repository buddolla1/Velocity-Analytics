package com.jira.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jira.analytics.config.JiraProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.util.StringUtils;

class JiraRestClient {

    private static final List<String> REQUIRED_FIELDS = List.of(
            "issuetype",
            "status",
            "project",
            "summary",
            "assignee",
            "resolutiondate",
            "updated"
    );

    private final JiraProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    JiraRestClient(JiraProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    SearchPage searchIssues(String baseUrl, String username, String apiToken, String jql, String nextPageToken) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("jql", jql);
        requestBody.put("maxResults", Math.max(1, properties.getSearchPageSize()));
        if (StringUtils.hasText(nextPageToken)) {
            requestBody.put("nextPageToken", nextPageToken);
        }

        ArrayNode fields = requestBody.putArray("fields");
        for (String field : buildRequestedFields()) {
            fields.add(field);
        }

        JsonNode response = executePost(baseUrl, username, apiToken, "/rest/api/3/search/jql", requestBody);
        return new SearchPage(readArray(response, "issues", "values"), text(response, "nextPageToken"));
    }

    ChangelogPage fetchStatusChangelogs(String baseUrl, String username, String apiToken, List<String> issueIdsOrKeys, String nextPageToken) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode issues = requestBody.putArray("issueIdsOrKeys");
        for (String issueIdOrKey : issueIdsOrKeys) {
            issues.add(issueIdOrKey);
        }
        ArrayNode fields = requestBody.putArray("fieldIds");
        fields.add("status");
        requestBody.put("maxResults", Math.max(1, properties.getChangelogPageSize()));
        if (StringUtils.hasText(nextPageToken)) {
            requestBody.put("nextPageToken", nextPageToken);
        }

        JsonNode response = executePost(baseUrl, username, apiToken, "/rest/api/3/changelog/bulkfetch", requestBody);
        return new ChangelogPage(readArray(response, "issueChangeLogs", "values", "changelogs"), text(response, "nextPageToken"));
    }

    void validateConfiguration(String baseUrl, String username, String apiToken) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Missing Jira base URL. Set JIRA_BASE_URL.");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("Missing Jira username. Set JIRA_USERNAME.");
        }
        if (!StringUtils.hasText(apiToken)) {
            throw new IllegalStateException("Missing Jira API token. Set JIRA_API_TOKEN.");
        }
    }

    private List<String> buildRequestedFields() {
        List<String> fields = new ArrayList<>(REQUIRED_FIELDS);
        if (StringUtils.hasText(properties.getStoryPointsField())) {
            fields.add(properties.getStoryPointsField().trim());
        }
        if (StringUtils.hasText(properties.getSprintField())) {
            fields.add(properties.getSprintField().trim());
        }
        if (StringUtils.hasText(properties.getSsoField())) {
            fields.add(properties.getSsoField().trim());
        }
        return fields;
    }

    private JsonNode executePost(String baseUrl, String username, String apiToken, String path, JsonNode body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolveUri(baseUrl, path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", basicAuthHeader(username, apiToken))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return execute(request);
    }

    private JsonNode execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                return StringUtils.hasText(body) ? objectMapper.readTree(body) : objectMapper.createObjectNode();
            }
            throw new IllegalStateException(buildErrorMessage(response.statusCode(), response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Jira request was interrupted.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Jira REST API.", exception);
        }
    }

    private URI resolveUri(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return URI.create(normalizedBaseUrl + path);
    }

    private String basicAuthHeader(String username, String apiToken) {
        String credentials = username.trim() + ":" + apiToken;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private URI resolveUri(String path) {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + path);
    }

    private List<JsonNode> readArray(JsonNode node, String... candidateNames) {
        for (String candidateName : candidateNames) {
            JsonNode candidate = node.get(candidateName);
            if (candidate != null && candidate.isArray()) {
                List<JsonNode> items = new ArrayList<>();
                candidate.forEach(items::add);
                return items;
            }
        }
        return List.of();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String buildErrorMessage(int statusCode, String body) {
        String trimmedBody = body == null ? "" : body.trim();
        if (!trimmedBody.isEmpty()) {
            return "Jira request failed with HTTP " + statusCode + ": " + trimmedBody;
        }
        return "Jira request failed with HTTP " + statusCode;
    }

    record SearchPage(List<JsonNode> issues, String nextPageToken) {
    }

    record ChangelogPage(List<JsonNode> issueChangeLogs, String nextPageToken) {
    }
}
