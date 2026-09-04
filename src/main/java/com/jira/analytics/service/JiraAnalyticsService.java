package com.jira.analytics.service;

import com.jira.analytics.dto.DashboardResponse;
import com.jira.analytics.dto.DashboardSummary;
import com.jira.analytics.dto.EmployeeMetrics;
import com.jira.analytics.dto.IssueTypeMetrics;
import com.jira.analytics.dto.JiraIssue;
import com.jira.analytics.dto.MonthlyVelocity;
import com.jira.analytics.dto.SprintMetrics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JiraAnalyticsService {

    private static final Set<String> COMPLETED_STATUSES = Set.of("done", "closed", "resolved");
    private static final Set<String> STORY_TYPES = Set.of("story");
    private static final Set<String> DEFECT_TYPES = Set.of("bug", "defect");

    private final JiraExcelParser parser;

    public JiraAnalyticsService(JiraExcelParser parser) {
        this.parser = parser;
    }

    public DashboardResponse process(MultipartFile file) {
        List<JiraIssue> issues = parser.parse(file);
        return buildResponse(issues);
    }

    private DashboardResponse buildResponse(List<JiraIssue> issues) {
        List<JiraIssue> completedIssues = issues.stream()
                .filter(this::isCompleted)
                .toList();

        DashboardSummary summary = buildSummary(issues, completedIssues);
        List<MonthlyVelocity> monthlyVelocity = buildMonthlyVelocity(completedIssues);
        List<SprintMetrics> sprintMetrics = buildSprintMetrics(issues);
        List<EmployeeMetrics> employeeMetrics = buildEmployeeMetrics(completedIssues);
        List<IssueTypeMetrics> issueTypeMetrics = buildIssueTypeMetrics(issues);

        return new DashboardResponse(summary, monthlyVelocity, sprintMetrics, employeeMetrics, issueTypeMetrics, issues);
    }

    private DashboardSummary buildSummary(List<JiraIssue> issues, List<JiraIssue> completedIssues) {
        long storiesCompleted = completedIssues.stream().filter(this::isStory).count();
        long defectsClosed = completedIssues.stream().filter(this::isDefect).count();
        double storyPointsCompleted = completedIssues.stream()
                .map(JiraIssue::storyPoints)
                .filter(value -> value != null && Double.isFinite(value))
                .mapToDouble(Double::doubleValue)
                .sum();

        TreeSet<String> contributors = completedIssues.stream()
                .map(JiraIssue::assignee)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(TreeSet::new));

        List<Double> cycleTimes = completedIssues.stream()
                .map(JiraIssue::cycleTimeDays)
                .filter(value -> value != null && Double.isFinite(value))
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

    private List<MonthlyVelocity> buildMonthlyVelocity(List<JiraIssue> completedIssues) {
        Map<String, MonthlyAccumulator> grouped = new TreeMap<>((left, right) -> left.compareTo(right));

        for (JiraIssue issue : completedIssues) {
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

    private List<SprintMetrics> buildSprintMetrics(List<JiraIssue> issues) {
        Map<String, List<JiraIssue>> grouped = new LinkedHashMap<>();
        for (JiraIssue issue : issues) {
            String sprint = normalizeText(issue.sprint());
            grouped.computeIfAbsent(sprint.isEmpty() ? "No Sprint" : sprint, unused -> new ArrayList<>()).add(issue);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    String sprint = entry.getKey();
                    List<JiraIssue> sprintIssues = entry.getValue();
                    List<JiraIssue> storyIssues = sprintIssues.stream().filter(this::isStory).toList();
                    List<JiraIssue> completedIssues = sprintIssues.stream().filter(this::isCompleted).toList();

                    long totalStories = storyIssues.size();
                    long completedStories = completedIssues.stream().filter(this::isStory).count();
                    double storyPoints = completedIssues.stream()
                            .map(JiraIssue::storyPoints)
                            .filter(value -> value != null && Double.isFinite(value))
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    long defects = completedIssues.stream().filter(this::isDefect).count();
                    long contributors = completedIssues.stream()
                            .map(JiraIssue::assignee)
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

    private List<EmployeeMetrics> buildEmployeeMetrics(List<JiraIssue> completedIssues) {
        Map<String, List<JiraIssue>> grouped = new LinkedHashMap<>();
        for (JiraIssue issue : completedIssues) {
            String employee = normalizeText(issue.assignee());
            grouped.computeIfAbsent(employee.isEmpty() ? "Unassigned" : employee, unused -> new ArrayList<>()).add(issue);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    String employee = entry.getKey();
                    List<JiraIssue> employeeIssues = entry.getValue();
                    List<Double> cycleTimes = employeeIssues.stream()
                            .map(JiraIssue::cycleTimeDays)
                            .filter(value -> value != null && Double.isFinite(value))
                            .toList();
                    long sprintCount = employeeIssues.stream()
                            .map(JiraIssue::sprint)
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .collect(Collectors.toSet())
                            .size();

                    double storyPointsCompleted = employeeIssues.stream()
                            .map(JiraIssue::storyPoints)
                            .filter(value -> value != null && Double.isFinite(value))
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

    private List<IssueTypeMetrics> buildIssueTypeMetrics(List<JiraIssue> issues) {
        Map<String, Long> grouped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (JiraIssue issue : issues) {
            String issueType = normalizeText(issue.issueType());
            grouped.merge(issueType.isEmpty() ? "Unknown" : issueType, 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(entry -> new IssueTypeMetrics(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean isCompleted(JiraIssue issue) {
        String status = normalizeText(issue.status()).toLowerCase();
        return COMPLETED_STATUSES.contains(status) || StringUtils.hasText(normalizeText(issue.resolved()));
    }

    private boolean isStory(JiraIssue issue) {
        return STORY_TYPES.contains(normalizeText(issue.issueType()).toLowerCase());
    }

    private boolean isDefect(JiraIssue issue) {
        return DEFECT_TYPES.contains(normalizeText(issue.issueType()).toLowerCase());
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeMonthName(String value) {
        String monthName = normalizeText(value);
        return monthName.isEmpty() ? "Unknown" : monthName;
    }

    private int normalizeMonthNumber(Integer value) {
        return value == null || value < 1 ? 99 : value;
    }

    private double safeDouble(Double value) {
        return value == null || !Double.isFinite(value) ? 0d : value;
    }

    private int compareAlphaNumeric(String left, String right) {
        return left.compareToIgnoreCase(right);
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
}
