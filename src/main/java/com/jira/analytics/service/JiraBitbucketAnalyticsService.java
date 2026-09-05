package com.jira.analytics.service;

import com.jira.analytics.dto.CycleStartSourceMetric;
import com.jira.analytics.dto.EmployeePrMetrics;
import com.jira.analytics.dto.JiraIssue;
import com.jira.analytics.dto.MonthlyPrMetrics;
import com.jira.analytics.dto.PrAnalyticsRecord;
import com.jira.analytics.dto.PrAnalyticsResponse;
import com.jira.analytics.dto.PrAnalyticsSummary;
import com.jira.analytics.dto.RepositoryPrMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JiraBitbucketAnalyticsService {

    private static final Set<String> MERGED_STATES = Set.of("merged");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.US);
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    private final JiraExcelParser jiraExcelParser;

    public JiraBitbucketAnalyticsService(JiraExcelParser jiraExcelParser) {
        this.jiraExcelParser = jiraExcelParser;
    }

    public PrAnalyticsResponse process(MultipartFile bitbucketFile, MultipartFile jiraFile) {
        List<JiraIssue> jiraIssues = jiraExcelParser.parse(jiraFile);
        Map<String, JiraIssue> jiraByKey = jiraIssues.stream()
                .filter(issue -> StringUtils.hasText(issue.issueKey()))
                .collect(Collectors.toMap(
                        issue -> normalizeKey(issue.issueKey()),
                        issue -> issue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<BitbucketRow> bitbucketRows = parseBitbucket(bitbucketFile);
        List<PrAnalyticsRecord> records = new ArrayList<>(bitbucketRows.size());

        for (BitbucketRow row : bitbucketRows) {
            JiraIssue matchedIssue = StringUtils.hasText(row.jiraKey) ? jiraByKey.get(normalizeKey(row.jiraKey)) : null;
            records.add(buildRecord(row, matchedIssue));
        }

        return buildResponse(records);
    }

    private PrAnalyticsResponse buildResponse(List<PrAnalyticsRecord> records) {
        List<PrAnalyticsRecord> mergedRecords = records.stream()
                .filter(record -> isMerged(record.state()))
                .toList();

        PrAnalyticsSummary summary = buildSummary(records, mergedRecords);
        List<MonthlyPrMetrics> monthlyMetrics = buildMonthlyMetrics(mergedRecords);
        List<EmployeePrMetrics> employeeMetrics = buildEmployeeMetrics(mergedRecords);
        List<RepositoryPrMetrics> repositoryMetrics = buildRepositoryMetrics(mergedRecords);
        List<CycleStartSourceMetric> cycleStartSources = buildCycleStartSources(mergedRecords);

        return new PrAnalyticsResponse(summary, monthlyMetrics, employeeMetrics, repositoryMetrics, cycleStartSources, records);
    }

    private PrAnalyticsSummary buildSummary(List<PrAnalyticsRecord> allRecords, List<PrAnalyticsRecord> mergedRecords) {
        long jiraMatchedPrs = allRecords.stream().filter(PrAnalyticsRecord::jiraMatch).count();
        long unmatchedPrs = allRecords.size() - jiraMatchedPrs;
        long activeAuthors = mergedRecords.stream()
                .map(this::employeeName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet())
                .size();

        List<Double> cycleTimes = mergedRecords.stream()
                .map(PrAnalyticsRecord::cycleTimeDays)
                .filter(this::isFiniteNumber)
                .toList();
        List<Double> firstCommitToMerge = mergedRecords.stream()
                .map(this::firstCommitToMergeDays)
                .filter(this::isFiniteNumber)
                .toList();
        List<Double> createdToMerge = mergedRecords.stream()
                .map(this::createdToMergeDays)
                .filter(this::isFiniteNumber)
                .toList();

        return new PrAnalyticsSummary(
                allRecords.size(),
                mergedRecords.size(),
                (int) jiraMatchedPrs,
                (int) unmatchedPrs,
                allRecords.isEmpty() ? 0d : (jiraMatchedPrs * 100.0d) / allRecords.size(),
                average(cycleTimes),
                median(cycleTimes),
                average(firstCommitToMerge),
                average(createdToMerge),
                (int) activeAuthors
        );
    }

    private List<MonthlyPrMetrics> buildMonthlyMetrics(List<PrAnalyticsRecord> mergedRecords) {
        Map<YearMonth, MonthlyAccumulator> grouped = new TreeMap<>();

        for (PrAnalyticsRecord record : mergedRecords) {
            LocalDateTime mergedAt = parseDateTime(record.prMergedAt());
            if (mergedAt == null) {
                continue;
            }

            YearMonth yearMonth = YearMonth.from(mergedAt);
            MonthlyAccumulator accumulator = grouped.computeIfAbsent(yearMonth, unused -> new MonthlyAccumulator());
            accumulator.prsMerged++;
            if (record.jiraMatch()) {
                accumulator.matchedPrs++;
            } else {
                accumulator.unmatchedPrs++;
            }

            Double cycleTimeDays = record.cycleTimeDays();
            if (isFiniteNumber(cycleTimeDays)) {
                accumulator.cycleTimes.add(cycleTimeDays);
            }
        }

        return grouped.entrySet().stream()
                .map(entry -> new MonthlyPrMetrics(
                        entry.getKey().getMonthValue(),
                        entry.getKey().getMonth().name().substring(0, 1) + entry.getKey().getMonth().name().substring(1).toLowerCase(Locale.ROOT),
                        entry.getValue().prsMerged,
                        average(entry.getValue().cycleTimes),
                        entry.getValue().matchedPrs,
                        entry.getValue().unmatchedPrs
                ))
                .toList();
    }

    private List<EmployeePrMetrics> buildEmployeeMetrics(List<PrAnalyticsRecord> mergedRecords) {
        Map<String, List<PrAnalyticsRecord>> grouped = new LinkedHashMap<>();
        for (PrAnalyticsRecord record : mergedRecords) {
            grouped.computeIfAbsent(employeeName(record), unused -> new ArrayList<>()).add(record);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<PrAnalyticsRecord> records = entry.getValue();
                    List<Double> cycleTimes = records.stream()
                            .map(PrAnalyticsRecord::cycleTimeDays)
                            .filter(this::isFiniteNumber)
                            .toList();
                    long repositories = records.stream()
                            .map(record -> normalizeText(record.repositoryName()))
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toSet())
                            .size();
                    long jiraMatched = records.stream().filter(PrAnalyticsRecord::jiraMatch).count();
                    return new EmployeePrMetrics(
                            entry.getKey(),
                            records.size(),
                            jiraMatched,
                            average(cycleTimes),
                            median(cycleTimes),
                            repositories
                    );
                })
                .toList();
    }

    private List<RepositoryPrMetrics> buildRepositoryMetrics(List<PrAnalyticsRecord> mergedRecords) {
        Map<String, List<PrAnalyticsRecord>> grouped = new LinkedHashMap<>();
        for (PrAnalyticsRecord record : mergedRecords) {
            String repository = normalizeText(record.repositoryName());
            grouped.computeIfAbsent(repository.isEmpty() ? "Unknown" : repository, unused -> new ArrayList<>()).add(record);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<PrAnalyticsRecord> records = entry.getValue();
                    List<Double> cycleTimes = records.stream()
                            .map(PrAnalyticsRecord::cycleTimeDays)
                            .filter(this::isFiniteNumber)
                            .toList();
                    long jiraMatched = records.stream().filter(PrAnalyticsRecord::jiraMatch).count();
                    double percentage = records.isEmpty() ? 0d : (jiraMatched * 100.0d) / records.size();
                    return new RepositoryPrMetrics(entry.getKey(), records.size(), average(cycleTimes), percentage);
                })
                .toList();
    }

    private List<CycleStartSourceMetric> buildCycleStartSources(List<PrAnalyticsRecord> mergedRecords) {
        Map<String, Long> grouped = mergedRecords.stream()
                .map(PrAnalyticsRecord::cycleStartSource)
                .map(this::normalizeSource)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        long total = grouped.values().stream().mapToLong(Long::longValue).sum();
        return grouped.entrySet().stream()
                .map(entry -> new CycleStartSourceMetric(
                        entry.getKey(),
                        entry.getValue(),
                        total == 0 ? 0d : (entry.getValue() * 100.0d) / total
                ))
                .toList();
    }

    private PrAnalyticsRecord buildRecord(BitbucketRow row, JiraIssue matchedIssue) {
        boolean jiraMatch = matchedIssue != null && StringUtils.hasText(row.jiraKey);
        LocalDateTime firstCommitAt = row.firstCommitAt;
        LocalDateTime prCreatedAt = row.prCreatedAt;
        LocalDateTime prMergedAt = row.prMergedAt;
        LocalDateTime jiraInProgressAt = jiraMatch
                ? firstNonNull(row.jiraInProgressAt, parseDateTime(matchedIssue.toDoToInProgress()))
                : null;

        StartChoice startChoice = determineStart(jiraMatch, firstCommitAt, prCreatedAt, jiraInProgressAt);
        Double cycleTimeHours = null;
        Double cycleTimeDays = null;
        String cycleStart = null;
        String cycleStartSource = null;

        if ("MERGED".equalsIgnoreCase(normalizeText(row.state)) && startChoice != null && prMergedAt != null) {
            if (!prMergedAt.isBefore(startChoice.timestamp)) {
                cycleStart = formatDateTime(startChoice.timestamp);
                cycleStartSource = startChoice.source;
                cycleTimeHours = Duration.between(startChoice.timestamp, prMergedAt).toMinutes() / 60.0d;
                cycleTimeDays = cycleTimeHours / 24.0d;
            }
        } else if (startChoice != null) {
            cycleStart = formatDateTime(startChoice.timestamp);
            cycleStartSource = startChoice.source;
        }

        if (!"MERGED".equalsIgnoreCase(normalizeText(row.state))) {
            cycleTimeHours = null;
            cycleTimeDays = null;
        }

        return new PrAnalyticsRecord(
                fallback(row.prId, row.pullRequestId),
                fallback(row.repositoryName, row.repository),
                row.projectName,
                row.authorName,
                row.authorUsername,
                row.state,
                row.prTitle,
                row.prDescription,
                row.sourceBranch,
                row.destinationBranch,
                row.jiraKey,
                jiraMatch,
                jiraMatch ? fallback(row.jiraAssignee, matchedIssue != null ? matchedIssue.assignee() : null) : null,
                jiraMatch ? fallback(row.jiraStatus, matchedIssue != null ? matchedIssue.status() : null) : null,
                jiraMatch ? fallback(row.jiraProjectName, matchedIssue != null ? matchedIssue.projectName() : null) : null,
                jiraMatch ? fallback(row.jiraSprint, matchedIssue != null ? matchedIssue.sprint() : null) : null,
                jiraMatch ? fallbackDouble(row.jiraStoryPoints, matchedIssue != null ? matchedIssue.storyPoints() : null) : null,
                jiraMatch ? formatDateTime(jiraInProgressAt) : null,
                formatDateTime(prCreatedAt),
                formatDateTime(firstCommitAt),
                cycleStart,
                cycleStartSource,
                formatDateTime(prMergedAt),
                cycleTimeHours,
                cycleTimeDays
        );
    }

    private List<BitbucketRow> parseBitbucket(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No Bitbucket file was uploaded.");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return List.of();
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                return List.of();
            }

            Map<String, Integer> headers = buildHeaderMap(headerRow, evaluator);
            List<BitbucketRow> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                rows.add(readBitbucketRow(row, headers, evaluator));
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid Bitbucket Excel file.");
        }
    }

    private BitbucketRow readBitbucketRow(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator) {
        return new BitbucketRow(
                readOptionalString(row, headers, evaluator, "prid", "pullrequestid", "bitbucketprid", "id"),
                readOptionalString(row, headers, evaluator, "pullrequestid", "prid", "bitbucketprid"),
                readOptionalString(row, headers, evaluator, "repositoryname", "repository", "reponame", "repo"),
                readOptionalString(row, headers, evaluator, "repository", "repositoryname", "reponame", "repo"),
                readOptionalString(row, headers, evaluator, "projectname", "project", "projectkey"),
                readOptionalString(row, headers, evaluator, "authorname", "author", "fullname", "createdby"),
                readOptionalString(row, headers, evaluator, "authorusername", "authoruser", "authorlogin", "username"),
                readOptionalString(row, headers, evaluator, "state", "prstate", "pullrequeststate"),
                readOptionalString(row, headers, evaluator, "prtitle", "title", "summary"),
                readOptionalString(row, headers, evaluator, "prdescription", "description"),
                readOptionalString(row, headers, evaluator, "sourcebranch", "sourcebranchname"),
                readOptionalString(row, headers, evaluator, "destinationbranch", "destinationbranchname"),
                readOptionalString(row, headers, evaluator, "jirakey", "jiraissuekey", "bitbucketjirakey", "issuekey"),
                readOptionalString(row, headers, evaluator, "jiraassignee"),
                readOptionalString(row, headers, evaluator, "jirastatus"),
                readOptionalString(row, headers, evaluator, "jiraprojectname"),
                readOptionalString(row, headers, evaluator, "jirasprint"),
                readOptionalDouble(row, headers, evaluator, "jirastorypoints"),
                readOptionalDateTime(row, headers, evaluator, "jiratodotoinprogress", "jirainprogressat", "todotoinprogress"),
                readOptionalDateTime(row, headers, evaluator, "bbprcreatedat", "prcreatedat", "createdat"),
                readOptionalDateTime(row, headers, evaluator, "bbfirstcommitcreatedat", "firstcommitcreatedat", "firstcommitat"),
                readOptionalDateTime(row, headers, evaluator, "bbprmergedat", "prmergedat", "mergedat")
        );
    }

    private Row findHeaderRow(Sheet sheet) {
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && !isBlankRow(row)) {
                return row;
            }
        }
        return null;
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow, FormulaEvaluator evaluator) {
        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : headerRow) {
            String normalized = normalizeHeader(readCellAsString(cell, evaluator));
            if (!normalized.isEmpty()) {
                headers.putIfAbsent(normalized, cell.getColumnIndex());
            }
        }
        return headers;
    }

    private String readOptionalString(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String... aliases) {
        for (String alias : aliases) {
            Integer index = headers.get(alias);
            if (index == null) {
                continue;
            }
            Cell cell = row.getCell(index);
            String value = readCellAsString(cell, evaluator);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Double readOptionalDouble(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String... aliases) {
        String value = readOptionalString(row, headers, evaluator, aliases);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDateTime readOptionalDateTime(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String... aliases) {
        String value = readOptionalString(row, headers, evaluator, aliases);
        return parseDateTime(value);
    }

    private String readCellAsString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return formatDateTime(LocalDateTime.ofInstant(cell.getDateCellValue().toInstant(), java.time.ZoneId.systemDefault()));
        }
        return DATA_FORMATTER.formatCellValue(cell, evaluator).trim();
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && StringUtils.hasText(DATA_FORMATTER.formatCellValue(cell).trim())) {
                return false;
            }
        }
        return true;
    }

    private String normalizeHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return "";
        }
        return header.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeKey(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private String fallback(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Double fallbackDouble(Double first, Double second) {
        return isFiniteNumber(first) ? first : second;
    }

    private String employeeName(PrAnalyticsRecord record) {
        if (record.jiraMatch() && StringUtils.hasText(record.jiraAssignee())) {
            return record.jiraAssignee();
        }
        return normalizeText(record.authorName()).isEmpty() ? "Unassigned" : record.authorName();
    }

    private boolean isMerged(String state) {
        return MERGED_STATES.contains(normalizeText(state).toLowerCase(Locale.ROOT));
    }

    private StartChoice determineStart(boolean jiraMatch, LocalDateTime firstCommitAt, LocalDateTime prCreatedAt, LocalDateTime jiraInProgressAt) {
        List<StartChoice> candidates = new ArrayList<>();
        if (firstCommitAt != null) {
            candidates.add(new StartChoice(firstCommitAt, "BB FIRST COMMIT"));
        }
        if (prCreatedAt != null) {
            candidates.add(new StartChoice(prCreatedAt, "BB PR CREATED"));
        }
        if (jiraMatch && jiraInProgressAt != null) {
            candidates.add(new StartChoice(jiraInProgressAt, "JIRA IN PROGRESS"));
        }
        return candidates.stream()
                .min(Comparator.comparing((StartChoice choice) -> choice.timestamp).thenComparing(choice -> choice.source))
                .orElse(null);
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }

            try {
                LocalDate date = LocalDate.parse(trimmed, formatter);
                return date.atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }

        try {
            return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }

        return null;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private Double firstCommitToMergeDays(PrAnalyticsRecord record) {
        LocalDateTime firstCommitAt = parseDateTime(record.firstCommitAt());
        LocalDateTime mergedAt = parseDateTime(record.prMergedAt());
        return differenceInDays(firstCommitAt, mergedAt);
    }

    private Double createdToMergeDays(PrAnalyticsRecord record) {
        LocalDateTime createdAt = parseDateTime(record.prCreatedAt());
        LocalDateTime mergedAt = parseDateTime(record.prMergedAt());
        return differenceInDays(createdAt, mergedAt);
    }

    private Double differenceInDays(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        return Duration.between(start, end).toMinutes() / 60.0d / 24.0d;
    }

    private Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
    }

    private Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0d;
    }

    private boolean isFiniteNumber(Double value) {
        return value != null && Double.isFinite(value);
    }

    private String normalizeSource(String source) {
        return source == null ? "" : source.trim();
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private record StartChoice(LocalDateTime timestamp, String source) {
    }

    private static final class MonthlyAccumulator {
        private long prsMerged;
        private long matchedPrs;
        private long unmatchedPrs;
        private final List<Double> cycleTimes = new ArrayList<>();
    }

    private record BitbucketRow(
            String prId,
            String pullRequestId,
            String repositoryName,
            String repository,
            String projectName,
            String authorName,
            String authorUsername,
            String state,
            String prTitle,
            String prDescription,
            String sourceBranch,
            String destinationBranch,
            String jiraKey,
            String jiraAssignee,
            String jiraStatus,
            String jiraProjectName,
            String jiraSprint,
            Double jiraStoryPoints,
            LocalDateTime jiraInProgressAt,
            LocalDateTime prCreatedAt,
            LocalDateTime firstCommitAt,
            LocalDateTime prMergedAt
    ) {
    }
}
