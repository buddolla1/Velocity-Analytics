package com.jira.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jira.analytics.config.JiraProperties;
import com.jira.analytics.dto.DashboardResponse;
import com.jira.analytics.dto.DashboardSummary;
import com.jira.analytics.dto.EmployeeMetrics;
import com.jira.analytics.dto.IssueTypeMetrics;
import com.jira.analytics.dto.JiraSyncRequest;
import com.jira.analytics.dto.MonthlyVelocity;
import com.jira.analytics.dto.SprintMetrics;
import com.syf.jirametrics.model.JiraIssueRecord;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JiraRestAnalyticsService {

    private static final Set<String> COMPLETED_STATUSES = Set.of("done", "closed", "resolved");
    private static final Set<String> STORY_TYPES = Set.of("story");
    private static final Set<String> DEFECT_TYPES = Set.of("bug", "defect");

    private final JiraProperties properties;
    private final JiraRestClient jiraRestClient;
    private final JiraIssueRecordRepository repository;

    public JiraRestAnalyticsService(JiraProperties properties, JiraIssueRecordRepository repository, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.jiraRestClient = new JiraRestClient(properties, objectMapper);
    }

    public DashboardResponse getDashboard() {
        return buildResponse(repository.findAll());
    }

    public DashboardResponse sync() {
        return sync(null);
    }

    public DashboardResponse sync(JiraSyncRequest request) {
        List<SyncTarget> targets = resolveTargets(request);
        OffsetDateTime syncedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<JiraIssueRecord> allRecords = new ArrayList<>();

        for (SyncTarget target : targets) {
            jiraRestClient.validateConfiguration(target.baseUrl(), target.username(), target.apiToken());

            String jql = resolveJql(target);
            List<JsonNode> allIssues = new ArrayList<>();
            String nextPageToken = null;
            do {
                JiraRestClient.SearchPage page = jiraRestClient.searchIssues(
                        target.baseUrl(),
                        target.username(),
                        target.apiToken(),
                        jql,
                        nextPageToken
                );
                allIssues.addAll(page.issues());
                nextPageToken = page.nextPageToken();
            } while (StringUtils.hasText(nextPageToken));

            List<String> issueIds = allIssues.stream()
                    .map(this::extractIssueId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            Map<String, TransitionTimeline> timelines = fetchTimelines(target, issueIds);
            List<JiraIssueRecord> records = allIssues.stream()
                    .map(issue -> buildRecord(issue, timelines.get(extractIssueId(issue)), syncedAt))
                    .filter(Objects::nonNull)
                    .toList();

            allRecords.addAll(records);
        }

        repository.saveAll(allRecords);
        return buildResponse(repository.findAll());
    }

    private Map<String, TransitionTimeline> fetchTimelines(SyncTarget target, List<String> issueIds) {
        Map<String, TransitionTimeline> timelines = new HashMap<>();
        for (List<String> batch : partition(issueIds, 1000)) {
            String nextPageToken = null;
            do {
                JiraRestClient.ChangelogPage page = jiraRestClient.fetchStatusChangelogs(
                        target.baseUrl(),
                        target.username(),
                        target.apiToken(),
                        batch,
                        nextPageToken
                );
                for (JsonNode issueLog : page.issueChangeLogs()) {
                    String issueId = extractIssueId(issueLog);
                    if (!StringUtils.hasText(issueId)) {
                        continue;
                    }
                    TransitionTimeline timeline = timelines.computeIfAbsent(issueId, unused -> new TransitionTimeline());
                    for (JsonNode history : extractHistories(issueLog)) {
                        captureTransition(history, timeline);
                    }
                }
                nextPageToken = page.nextPageToken();
            } while (StringUtils.hasText(nextPageToken));
        }
        return timelines;
    }

    private void captureTransition(JsonNode history, TransitionTimeline timeline) {
        OffsetDateTime createdAt = parseOffsetDateTime(history.path("created"));
        if (createdAt == null) {
            return;
        }

        for (JsonNode item : readArray(history, "items")) {
            String field = normalizeText(item.path("field").asText(null));
            if (!"status".equalsIgnoreCase(field)) {
                continue;
            }

            String from = normalizeTransitionName(item.path("fromString").asText(null));
            String to = normalizeTransitionName(item.path("toString").asText(null));
            if (timeline.toDoToInProgressAt == null && from.equals("todo") && to.equals("inprogress")) {
                timeline.toDoToInProgressAt = createdAt;
            } else if (timeline.inProgressToValidationAt == null && from.equals("inprogress") && to.equals("validation")) {
                timeline.inProgressToValidationAt = createdAt;
            } else if (timeline.validationToDoneAt == null && from.equals("validation") && to.equals("done")) {
                timeline.validationToDoneAt = createdAt;
            }
        }
    }

    private JiraIssueRecord buildRecord(JsonNode issueNode, TransitionTimeline timeline, OffsetDateTime syncedAt) {
        JsonNode fields = issueNode.path("fields");
        String issueId = extractIssueId(issueNode);
        String issueKey = normalizeText(issueNode.path("key").asText(null));
        if (!StringUtils.hasText(issueId) || !StringUtils.hasText(issueKey)) {
            return null;
        }

        OffsetDateTime resolvedAt = parseOffsetDateTime(fields.path("resolutiondate"));
        OffsetDateTime jiraUpdatedAt = parseOffsetDateTime(fields.path("updated"));
        OffsetDateTime monthSource = resolvedAt != null ? resolvedAt : jiraUpdatedAt;
        Month month = monthSource == null ? null : monthSource.getMonth();

        Double cycleTimeDays = null;
        if (timeline != null && timeline.toDoToInProgressAt != null && timeline.validationToDoneAt != null) {
            Duration duration = Duration.between(timeline.toDoToInProgressAt, timeline.validationToDoneAt);
            if (!duration.isNegative()) {
                cycleTimeDays = duration.toMillis() / 86_400_000d;
            }
        }

        return new JiraIssueRecord(
                null,
                issueId,
                issueKey,
                normalizeText(extractText(fields.path("issuetype").path("name"))),
                normalizeText(extractText(fields.path("status").path("name"))),
                normalizeText(extractText(fields.path("project").path("key"))),
                normalizeText(extractText(fields.path("project").path("name"))),
                normalizeText(extractText(fields.path("summary"))),
                parseDouble(fields.get(properties.getStoryPointsField())),
                extractSprint(fields.get(properties.getSprintField())),
                extractText(fields.path("assignee")),
                StringUtils.hasText(properties.getSsoField()) ? extractText(fields.get(properties.getSsoField())) : null,
                resolvedAt,
                month == null ? null : month.getValue(),
                month == null ? null : month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US),
                timeline == null ? null : timeline.toDoToInProgressAt,
                timeline == null ? null : timeline.inProgressToValidationAt,
                timeline == null ? null : timeline.validationToDoneAt,
                cycleTimeDays,
                jiraUpdatedAt,
                syncedAt
        );
    }

    private DashboardResponse buildResponse(List<JiraIssueRecord> issues) {
        List<JiraIssueRecord> completedIssues = issues.stream()
                .filter(this::isCompleted)
                .toList();

        DashboardSummary summary = buildSummary(issues, completedIssues);
        List<MonthlyVelocity> monthlyVelocity = buildMonthlyVelocity(completedIssues);
        List<SprintMetrics> sprintMetrics = buildSprintMetrics(issues);
        List<EmployeeMetrics> employeeMetrics = buildEmployeeMetrics(completedIssues);
        List<IssueTypeMetrics> issueTypeMetrics = buildIssueTypeMetrics(issues);

        return new DashboardResponse(summary, monthlyVelocity, sprintMetrics, employeeMetrics, issueTypeMetrics, issues);
    }

    private DashboardSummary buildSummary(List<JiraIssueRecord> issues, List<JiraIssueRecord> completedIssues) {
        long storiesCompleted = completedIssues.stream().filter(this::isStory).count();
        long defectsClosed = completedIssues.stream().filter(this::isDefect).count();
        double storyPointsCompleted = completedIssues.stream()
                .map(JiraIssueRecord::storyPoints)
                .filter(this::isFiniteNumber)
                .mapToDouble(Double::doubleValue)
                .sum();

        TreeSet<String> contributors = completedIssues.stream()
                .map(JiraIssueRecord::assignee)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(TreeSet::new));

        List<Double> cycleTimes = completedIssues.stream()
                .map(JiraIssueRecord::jiraCycleTimeDays)
                .filter(this::isFiniteNumber)
                .toList();

        Double averageCycleTimeDays = cycleTimes.isEmpty()
                ? null
                : cycleTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0d);

        return new DashboardSummary(
                issues.size(),
                storiesCompleted,
                storyPointsCompleted,
                defectsClosed,
                contributors.size(),
                averageCycleTimeDays
        );
    }

    private List<MonthlyVelocity> buildMonthlyVelocity(List<JiraIssueRecord> completedIssues) {
        Map<String, MonthlyAccumulator> grouped = new TreeMap<>();

        for (JiraIssueRecord issue : completedIssues) {
            String monthName = normalizeMonthName(issue.monthName());
            int monthNumber = normalizeMonthNumber(issue.monthNumber());
            String key = String.format("%02d-%s", monthNumber, monthName);
            MonthlyAccumulator accumulator = grouped.computeIfAbsent(key, unused -> new MonthlyAccumulator(monthNumber, monthName));
            if (isStory(issue)) {
                accumulator.storiesCompleted++;
            }
            if (isDefect(issue)) {
                accumulator.defectsCompleted++;
            }
            accumulator.storyPointsCompleted += safeDouble(issue.storyPoints());
        }

        return grouped.values().stream()
                .map(value -> new MonthlyVelocity(
                        value.monthNumber,
                        value.monthName,
                        value.storiesCompleted,
                        value.storyPointsCompleted,
                        value.defectsCompleted
                ))
                .toList();
    }

    private List<SprintMetrics> buildSprintMetrics(List<JiraIssueRecord> issues) {
        Map<String, List<JiraIssueRecord>> grouped = new LinkedHashMap<>();
        for (JiraIssueRecord issue : issues) {
            String sprint = normalizeText(issue.sprint());
            grouped.computeIfAbsent(sprint.isEmpty() ? "No Sprint" : sprint, unused -> new ArrayList<>()).add(issue);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    String sprint = entry.getKey();
                    List<JiraIssueRecord> sprintIssues = entry.getValue();
                    List<JiraIssueRecord> storyIssues = sprintIssues.stream().filter(this::isStory).toList();
                    List<JiraIssueRecord> completedIssues = sprintIssues.stream().filter(this::isCompleted).toList();

                    long totalStories = storyIssues.size();
                    long completedStories = completedIssues.stream().filter(this::isStory).count();
                    double storyPoints = completedIssues.stream()
                            .map(JiraIssueRecord::storyPoints)
                            .filter(this::isFiniteNumber)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    long defects = completedIssues.stream().filter(this::isDefect).count();
                    long contributors = completedIssues.stream()
                            .map(JiraIssueRecord::assignee)
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .collect(Collectors.toSet())
                            .size();

                    double completionPercentage = totalStories == 0 ? 0d : (completedStories * 100.0d) / totalStories;

                    return new SprintMetrics(
                            sprint,
                            totalStories,
                            completedStories,
                            storyPoints,
                            completionPercentage,
                            defects,
                            contributors
                    );
                })
                .sorted((left, right) -> compareAlphaNumeric(left.sprint(), right.sprint()))
                .toList();
    }

    private List<EmployeeMetrics> buildEmployeeMetrics(List<JiraIssueRecord> completedIssues) {
        Map<String, List<JiraIssueRecord>> grouped = new LinkedHashMap<>();
        for (JiraIssueRecord issue : completedIssues) {
            String employee = normalizeText(issue.assignee());
            grouped.computeIfAbsent(employee.isEmpty() ? "Unassigned" : employee, unused -> new ArrayList<>()).add(issue);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    String employee = entry.getKey();
                    List<JiraIssueRecord> employeeIssues = entry.getValue();
                    List<Double> cycleTimes = employeeIssues.stream()
                            .map(JiraIssueRecord::jiraCycleTimeDays)
                            .filter(this::isFiniteNumber)
                            .toList();
                    long sprintCount = employeeIssues.stream()
                            .map(JiraIssueRecord::sprint)
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .collect(Collectors.toSet())
                            .size();

                    double storyPointsCompleted = employeeIssues.stream()
                            .map(JiraIssueRecord::storyPoints)
                            .filter(this::isFiniteNumber)
                            .mapToDouble(Double::doubleValue)
                            .sum();

                    Double averageCycleTimeDays = cycleTimes.isEmpty()
                            ? null
                            : cycleTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0d);

                    return new EmployeeMetrics(
                            employee,
                            employeeIssues.stream().filter(this::isStory).count(),
                            storyPointsCompleted,
                            employeeIssues.stream().filter(this::isDefect).count(),
                            sprintCount,
                            averageCycleTimeDays
                    );
                })
                .sorted((left, right) -> {
                    int byPoints = Double.compare(right.storyPointsCompleted(), left.storyPointsCompleted());
                    return byPoints != 0 ? byPoints : compareAlphaNumeric(left.employee(), right.employee());
                })
                .toList();
    }

    private List<IssueTypeMetrics> buildIssueTypeMetrics(List<JiraIssueRecord> issues) {
        Map<String, Long> grouped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (JiraIssueRecord issue : issues) {
            String issueType = normalizeText(issue.issueType());
            grouped.merge(issueType.isEmpty() ? "Unknown" : issueType, 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(entry -> new IssueTypeMetrics(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean isCompleted(JiraIssueRecord issue) {
        String status = normalizeTransitionName(issue.status());
        return COMPLETED_STATUSES.contains(status) || issue.resolvedAt() != null;
    }

    private boolean isStory(JiraIssueRecord issue) {
        return STORY_TYPES.contains(normalizeText(issue.issueType()).toLowerCase(Locale.ROOT));
    }

    private boolean isDefect(JiraIssueRecord issue) {
        return DEFECT_TYPES.contains(normalizeText(issue.issueType()).toLowerCase(Locale.ROOT));
    }

    private List<SyncTarget> resolveTargets(JiraSyncRequest request) {
        List<String> usernames = parseCsvValues(valueOrFallback(request == null ? null : request.username(), properties.getUsername()));
        List<String> apiTokens = parseCsvValues(valueOrFallback(request == null ? null : request.apiToken(), properties.getApiToken()));
        List<String> projectKeys = parseCsvValues(valueOrFallback(request == null ? null : request.projectKey(), properties.getProjectKey()));
        String endDate = resolveEndDate(request);

        String baseUrl = valueOrFallback(null, properties.getBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Missing Jira base URL. Set JIRA_BASE_URL.");
        }

        if (usernames.isEmpty() || apiTokens.isEmpty()) {
            throw new IllegalStateException("Provide at least one Jira username and API token.");
        }

        if (projectKeys.isEmpty()) {
            if (StringUtils.hasText(properties.getJql())) {
                return List.of(new SyncTarget(baseUrl, usernames.get(0), apiTokens.get(0), null, endDate, properties.getJql().trim()));
            }
            throw new IllegalStateException("Provide at least one Jira project key or configure JIRA_JQL.");
        }

        int maxCount = Math.max(projectKeys.size(), Math.max(usernames.size(), apiTokens.size()));
        if (!isCompatibleCount(usernames.size(), maxCount) || !isCompatibleCount(apiTokens.size(), maxCount) || !isCompatibleCount(projectKeys.size(), maxCount)) {
            throw new IllegalStateException("Comma-separated Jira usernames, API tokens, and project keys must either have the same number of values or use a single value to apply to all targets.");
        }

        List<SyncTarget> targets = new ArrayList<>(maxCount);
        for (int index = 0; index < maxCount; index++) {
            String username = usernames.size() == 1 ? usernames.get(0) : usernames.get(index);
            String apiToken = apiTokens.size() == 1 ? apiTokens.get(0) : apiTokens.get(index);
            String projectKey = projectKeys.size() == 1 ? projectKeys.get(0) : projectKeys.get(index);
            targets.add(new SyncTarget(baseUrl, username, apiToken, projectKey, endDate, null));
        }
        return targets;
    }

    private String resolveEndDate(JiraSyncRequest request) {
        String suppliedDate = request == null ? null : request.endDate();
        String candidate = StringUtils.hasText(suppliedDate) ? suppliedDate.trim() : LocalDate.now().toString();
        try {
            LocalDate.parse(candidate);
            return candidate;
        } catch (Exception exception) {
            throw new IllegalStateException("End date must use yyyy-MM-dd format.");
        }
    }

    private boolean isCompatibleCount(int valueCount, int targetCount) {
        return valueCount == 1 || valueCount == targetCount;
    }

    private List<String> parseCsvValues(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(org.springframework.util.StringUtils.commaDelimitedListToStringArray(value))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String valueOrFallback(String requestValue, String fallbackValue) {
        return StringUtils.hasText(requestValue) ? requestValue.trim() : fallbackValue;
    }

    private String resolveJql(SyncTarget target) {
        if (StringUtils.hasText(target.jql())) {
            return composeJql(target.jql().trim(), target.endDate());
        }
        if (StringUtils.hasText(properties.getJql())) {
            return composeJql(properties.getJql().trim(), target.endDate());
        }
        if (StringUtils.hasText(target.projectKey())) {
            return composeJql("project = " + quoteIfNeeded(target.projectKey().trim()), target.endDate());
        }
        throw new IllegalStateException("Provide a Jira project key or configure JIRA_JQL before syncing Jira data.");
    }

    private String composeJql(String baseJql, String endDate) {
        String trimmed = baseJql.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int orderByIndex = lower.indexOf(" order by ");
        String ordering = null;
        if (orderByIndex >= 0) {
            ordering = trimmed.substring(orderByIndex + 1).trim();
            trimmed = trimmed.substring(0, orderByIndex).trim();
        }

        if (StringUtils.hasText(endDate)) {
            trimmed = trimmed + " AND updated <= \"" + endDate + "\"";
        }

        if (StringUtils.hasText(ordering)) {
            return trimmed + " " + ordering;
        }
        return trimmed + " ORDER BY updated ASC, id ASC";
    }

    private String quoteIfNeeded(String value) {
        if (value.contains(" ") || value.contains("-")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private String extractIssueId(JsonNode node) {
        String issueId = normalizeText(node.path("id").asText(null));
        if (StringUtils.hasText(issueId)) {
            return issueId;
        }
        issueId = normalizeText(node.path("issueId").asText(null));
        if (StringUtils.hasText(issueId)) {
            return issueId;
        }
        issueId = normalizeText(node.path("issueIdOrKey").asText(null));
        return StringUtils.hasText(issueId) ? issueId : null;
    }

    private List<JsonNode> extractHistories(JsonNode issueLog) {
        List<JsonNode> histories = new ArrayList<>();
        for (String candidate : List.of("changeHistories", "histories", "values")) {
            JsonNode node = issueLog.get(candidate);
            if (node != null && node.isArray()) {
                node.forEach(histories::add);
            }
        }
        if (histories.isEmpty() && issueLog.has("items")) {
            histories.add(issueLog);
        }
        histories.sort(Comparator.comparing(this::parseOffsetDateTimeFromHistory, Comparator.nullsLast(Comparator.naturalOrder())));
        return histories;
    }

    private OffsetDateTime parseOffsetDateTimeFromHistory(JsonNode history) {
        return parseOffsetDateTime(history.path("created"));
    }

    private List<JsonNode> readArray(JsonNode node, String fieldName) {
        JsonNode candidate = node.get(fieldName);
        if (candidate == null || !candidate.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        candidate.forEach(items::add);
        return items;
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isValueNode()) {
            String text = node.asText(null);
            return normalizeText(text);
        }
        if (node.isArray()) {
            String lastText = null;
            for (JsonNode item : node) {
                String text = extractText(item);
                if (StringUtils.hasText(text)) {
                    lastText = text;
                }
            }
            return lastText;
        }
        for (String fieldName : List.of("displayName", "name", "value", "key", "emailAddress", "accountId", "summary")) {
            String text = normalizeText(node.path(fieldName).asText(null));
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return normalizeText(node.asText(null));
    }

    private Double parseDouble(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        String text = normalizeText(node.asText(null));
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extractSprint(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            String sprint = null;
            for (JsonNode item : node) {
                String candidate = extractSprint(item);
                if (StringUtils.hasText(candidate)) {
                    sprint = candidate;
                }
            }
            return sprint;
        }
        if (node.isObject()) {
            String name = normalizeText(node.path("name").asText(null));
            if (StringUtils.hasText(name)) {
                return name;
            }
            String value = normalizeText(node.path("value").asText(null));
            if (StringUtils.hasText(value)) {
                return value;
            }
            return normalizeText(node.asText(null));
        }
        String text = normalizeText(node.asText(null));
        if (!StringUtils.hasText(text)) {
            return null;
        }
        int nameIndex = text.indexOf("name=");
        if (nameIndex >= 0) {
            int end = text.indexOf(',', nameIndex);
            String candidate = end > nameIndex ? text.substring(nameIndex + 5, end) : text.substring(nameIndex + 5);
            return normalizeText(candidate);
        }
        return text;
    }

    private OffsetDateTime parseOffsetDateTime(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                long epoch = node.asLong();
                if (Math.abs(epoch) < 1_000_000_000_000L) {
                    epoch *= 1000L;
                }
                return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
            }

            String text = normalizeText(node.asText(null));
            if (!StringUtils.hasText(text)) {
                return null;
            }
            if (text.matches("^-?\\d+$")) {
                long epoch = Long.parseLong(text);
                if (Math.abs(epoch) < 1_000_000_000_000L) {
                    epoch *= 1000L;
                }
                return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
            }
            try {
                return OffsetDateTime.parse(text);
            } catch (DateTimeParseException ignored) {
                return java.time.LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
            }
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTransitionName(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String normalizeMonthName(String value) {
        String monthName = normalizeText(value);
        return monthName.isEmpty() ? "Unknown" : monthName;
    }

    private int normalizeMonthNumber(Integer value) {
        return value == null || value < 1 ? 99 : value;
    }

    private boolean isFiniteNumber(Double value) {
        return value != null && Double.isFinite(value);
    }

    private double safeDouble(Double value) {
        return isFiniteNumber(value) ? value : 0d;
    }

    private int compareAlphaNumeric(String left, String right) {
        return left.compareToIgnoreCase(right);
    }

    private List<List<String>> partition(List<String> values, int batchSize) {
        List<List<String>> partitions = new ArrayList<>();
        for (int index = 0; index < values.size(); index += batchSize) {
            partitions.add(values.subList(index, Math.min(index + batchSize, values.size())));
        }
        return partitions;
    }

    private static final class MonthlyAccumulator {
        private final int monthNumber;
        private final String monthName;
        private long storiesCompleted;
        private double storyPointsCompleted;
        private long defectsCompleted;

        private MonthlyAccumulator(int monthNumber, String monthName) {
            this.monthNumber = monthNumber;
            this.monthName = monthName;
        }
    }

    private static final class TransitionTimeline {
        private OffsetDateTime toDoToInProgressAt;
        private OffsetDateTime inProgressToValidationAt;
        private OffsetDateTime validationToDoneAt;
    }

    private record SyncTarget(String baseUrl, String username, String apiToken, String projectKey, String endDate, String jql) {
    }
}
