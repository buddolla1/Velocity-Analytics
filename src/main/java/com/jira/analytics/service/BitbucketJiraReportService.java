package com.jira.analytics.service;

import com.jira.analytics.dto.CycleStartSourceMetric;
import com.jira.analytics.dto.EmployeePrMetrics;
import com.jira.analytics.dto.MonthlyPrMetrics;
import com.jira.analytics.dto.PrAnalyticsRecord;
import com.jira.analytics.dto.PrAnalyticsResponse;
import com.jira.analytics.dto.PrAnalyticsSummary;
import com.jira.analytics.dto.RepositoryPrMetrics;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.time.YearMonth;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BitbucketJiraReportService {

    private static final DateTimeFormatter OUTPUT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
    private static final LocalDate REPORT_START = LocalDate.of(2026, 1, 1);
    private static final int TOTAL_WEEKS = 13;

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a"),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    public byte[] generateReport(MultipartFile bitbucketFile, MultipartFile jiraFile) {
        validateFile(bitbucketFile, "Bitbucket file");
        validateFile(jiraFile, "Jira file");

        try (Workbook bitbucketWorkbook = new XSSFWorkbook(bitbucketFile.getInputStream());
             Workbook jiraWorkbook = new XSSFWorkbook(jiraFile.getInputStream());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            JiraLookup jiraLookup = readAllJiraData(jiraWorkbook);
            updateBitbucketWorkbook(bitbucketWorkbook, jiraLookup);
            createWeeklyReport(bitbucketWorkbook);
            bitbucketWorkbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate Bitbucket/Jira report", exception);
        }
    }

    public PrAnalyticsResponse generateAnalytics(MultipartFile bitbucketFile, MultipartFile jiraFile) {
        validateFile(bitbucketFile, "Bitbucket file");
        validateFile(jiraFile, "Jira file");

        try (Workbook bitbucketWorkbook = new XSSFWorkbook(bitbucketFile.getInputStream());
             Workbook jiraWorkbook = new XSSFWorkbook(jiraFile.getInputStream())) {
            JiraLookup jiraLookup = readAllJiraData(jiraWorkbook);
            List<BitbucketRow> bitbucketRows = readBitbucketRows(bitbucketWorkbook);
            List<PrAnalyticsRecord> records = buildRecords(bitbucketRows, jiraLookup);
            return buildResponse(records);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate Bitbucket/Jira analytics", exception);
        }
    }

    private JiraLookup readAllJiraData(Workbook jiraWorkbook) {
        Sheet jiraSheet = jiraWorkbook.getSheetAt(0);
        Row header = jiraSheet.getRow(0);
        if (header == null) {
            throw new IllegalStateException("Jira header row not found");
        }

        List<String> jiraHeaders = new ArrayList<>();
        int lastColumn = header.getLastCellNum();
        for (int column = 0; column < lastColumn; column++) {
            String headerName = getString(header.getCell(column));
            if (headerName.isBlank()) {
                headerName = "Column " + (column + 1);
            }
            jiraHeaders.add(headerName);
        }

        int issueKeyColumn = findColumn(header, "Issue key");
        validateColumn(issueKeyColumn, "Issue key");

        int inProgressColumn = findColumn(header, "To Do -> In Progress");
        validateColumn(inProgressColumn, "To Do -> In Progress");

        Map<String, JiraData> jiraDataMap = new HashMap<>();
        for (int rowIndex = 1; rowIndex <= jiraSheet.getLastRowNum(); rowIndex++) {
            Row row = jiraSheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String issueKey = normalizeJiraKey(getString(row.getCell(issueKeyColumn)));
            if (issueKey.isBlank()) {
                continue;
            }

            JiraData jiraData = new JiraData();
            jiraData.issueKey = issueKey;
            jiraData.inProgressAt = getDateTime(row.getCell(inProgressColumn));
            jiraData.cells = new ArrayList<>();

            for (int column = 0; column < lastColumn; column++) {
                jiraData.cells.add(readCellValue(row.getCell(column)));
            }

            jiraDataMap.putIfAbsent(issueKey, jiraData);
        }

        return new JiraLookup(jiraHeaders, jiraDataMap);
    }

    private List<BitbucketRow> readBitbucketRows(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new IllegalStateException("Bitbucket header row not found");
        }

        int jiraKeyColumn = findColumn(header, "Jira Key", "JIRA Key", "Bitbucket Jira Key");
        int prCreatedColumn = findColumn(header, "PR Created At");
        int firstCommitColumn = findColumn(header, "First Commit Created At", "First Commit At");
        int prMergedColumn = findColumn(header, "PR Merged At");

        validateColumn(jiraKeyColumn, "Jira Key");
        validateColumn(prCreatedColumn, "PR Created At");
        validateColumn(firstCommitColumn, "First Commit Created At");
        validateColumn(prMergedColumn, "PR Merged At");

        List<BitbucketRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row bitbucketRow = sheet.getRow(rowIndex);
            if (bitbucketRow == null) {
                continue;
            }
            rows.add(readBitbucketRow(bitbucketRow, jiraKeyColumn, prCreatedColumn, firstCommitColumn, prMergedColumn));
        }
        return rows;
    }

    private List<PrAnalyticsRecord> buildRecords(List<BitbucketRow> bitbucketRows, JiraLookup jiraLookup) {
        List<PrAnalyticsRecord> records = new ArrayList<>(bitbucketRows.size());
        for (BitbucketRow row : bitbucketRows) {
            JiraData jiraData = StringUtils.hasText(row.jiraKey) ? jiraLookup.dataByIssueKey.get(row.jiraKey) : null;
            records.add(buildRecord(row, jiraData, jiraLookup));
        }
        return records;
    }

    private PrAnalyticsRecord buildRecord(BitbucketRow row, JiraData jiraData, JiraLookup jiraLookup) {
        boolean jiraMatch = jiraData != null && StringUtils.hasText(row.jiraKey);
        LocalDateTime jiraInProgressAt = jiraMatch ? jiraData.inProgressAt : null;
        EarliestResult earliest = jiraMatch
                ? findEarliest(row.prCreatedAt, "BB PR CREATED", jiraInProgressAt, "JIRA IN PROGRESS", row.firstCommitAt, "BB FIRST COMMIT")
                : findEarliest(row.prCreatedAt, "BB PR CREATED", row.firstCommitAt, "BB FIRST COMMIT", null, null);

        LocalDateTime cycleStartDate = earliest.dateTime();
        Double cycleTimeHours = null;
        Double cycleTimeDays = null;
        if ("MERGED".equalsIgnoreCase(normalizeText(row.state))
                && cycleStartDate != null
                && row.prMergedAt != null
                && !row.prMergedAt.isBefore(cycleStartDate)) {
            cycleTimeHours = Duration.between(cycleStartDate, row.prMergedAt).toMinutes() / 60.0d;
            cycleTimeDays = cycleTimeHours / 24.0d;
        }

        return new PrAnalyticsRecord(
                row.prId,
                row.repositoryName,
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
                jiraMatch ? firstNonBlank(row.jiraAssignee, jiraString(jiraLookup, jiraData, "Assignee")) : null,
                jiraMatch ? firstNonBlank(row.jiraStatus, jiraString(jiraLookup, jiraData, "Status")) : null,
                jiraMatch ? firstNonBlank(row.jiraProjectName, jiraString(jiraLookup, jiraData, "Project Name", "Project")) : null,
                jiraMatch ? firstNonBlank(row.jiraSprint, jiraString(jiraLookup, jiraData, "Sprint")) : null,
                jiraMatch ? firstDouble(row.jiraStoryPoints, jiraDouble(jiraLookup, jiraData, "Story Points")) : null,
                jiraMatch ? formatDateTime(jiraInProgressAt) : null,
                formatDateTime(row.prCreatedAt),
                formatDateTime(row.firstCommitAt),
                formatDateTime(cycleStartDate),
                earliest.source(),
                formatDateTime(row.prMergedAt),
                cycleTimeHours,
                cycleTimeDays
        );
    }

    private PrAnalyticsResponse buildResponse(List<PrAnalyticsRecord> records) {
        List<PrAnalyticsRecord> mergedRecords = records.stream()
                .filter(this::isMerged)
                .toList();

        PrAnalyticsSummary summary = buildSummary(records, mergedRecords);
        List<MonthlyPrMetrics> monthlyMetrics = buildMonthlyMetrics(mergedRecords);
        List<EmployeePrMetrics> employeeMetrics = buildEmployeeMetrics(mergedRecords);
        List<RepositoryPrMetrics> repositoryMetrics = buildRepositoryMetrics(mergedRecords);
        List<CycleStartSourceMetric> cycleStartSources = buildCycleStartSources(mergedRecords);

        return new PrAnalyticsResponse(summary, monthlyMetrics, employeeMetrics, repositoryMetrics, cycleStartSources, records);
    }

    private String jiraString(JiraLookup lookup, JiraData jiraData, String... headerNames) {
        if (jiraData == null) {
            return null;
        }
        int column = findJiraColumn(lookup.headers, headerNames);
        if (column < 0 || column >= jiraData.cells.size()) {
            return null;
        }
        CellValueHolder holder = jiraData.cells.get(column);
        return holder == null ? null : normalizeText(holder.asString());
    }

    private Double jiraDouble(JiraLookup lookup, JiraData jiraData, String... headerNames) {
        if (jiraData == null) {
            return null;
        }
        int column = findJiraColumn(lookup.headers, headerNames);
        if (column < 0 || column >= jiraData.cells.size()) {
            return null;
        }
        CellValueHolder holder = jiraData.cells.get(column);
        return holder == null ? null : holder.asDouble();
    }

    private int findJiraColumn(List<String> headers, String... headerNames) {
        for (int index = 0; index < headers.size(); index++) {
            String normalized = normalizeHeader(headers.get(index));
            for (String headerName : headerNames) {
                if (normalized.equals(normalizeHeader(headerName))) {
                    return index;
                }
            }
        }
        return -1;
    }

    private PrAnalyticsSummary buildSummary(List<PrAnalyticsRecord> allRecords, List<PrAnalyticsRecord> mergedRecords) {
        long jiraMatchedPrs = allRecords.stream().filter(PrAnalyticsRecord::jiraMatch).count();
        long unmatchedPrs = allRecords.size() - jiraMatchedPrs;

        List<Double> cycleTimes = mergedRecords.stream()
                .map(PrAnalyticsRecord::cycleTimeDays)
                .filter(this::isFinite)
                .toList();
        List<Double> firstCommitToMerge = mergedRecords.stream()
                .map(record -> differenceInDays(parseDateTime(record.firstCommitAt()), parseDateTime(record.prMergedAt())))
                .filter(this::isFinite)
                .toList();
        List<Double> createdToMerge = mergedRecords.stream()
                .map(record -> differenceInDays(parseDateTime(record.prCreatedAt()), parseDateTime(record.prMergedAt())))
                .filter(this::isFinite)
                .toList();

        long activeAuthors = mergedRecords.stream()
                .map(this::resolveEmployeeName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet())
                .size();

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
        Map<YearMonth, MonthlyAccumulator> grouped = new HashMap<>();

        for (PrAnalyticsRecord record : mergedRecords) {
            LocalDateTime mergedAt = parseDateTime(record.prMergedAt());
            if (mergedAt == null) {
                continue;
            }
            YearMonth month = YearMonth.from(mergedAt);
            MonthlyAccumulator accumulator = grouped.computeIfAbsent(month, unused -> new MonthlyAccumulator());
            accumulator.prsMerged++;
            if (record.jiraMatch()) {
                accumulator.matchedPrs++;
            } else {
                accumulator.unmatchedPrs++;
            }
            if (isFinite(record.cycleTimeDays())) {
                accumulator.cycleTimes.add(record.cycleTimeDays());
            }
        }

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
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
            grouped.computeIfAbsent(resolveEmployeeName(record), unused -> new ArrayList<>()).add(record);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<PrAnalyticsRecord> employeeRecords = entry.getValue();
                    List<Double> cycleTimes = employeeRecords.stream()
                            .map(PrAnalyticsRecord::cycleTimeDays)
                            .filter(this::isFinite)
                            .toList();
                    long repositories = employeeRecords.stream()
                            .map(PrAnalyticsRecord::repositoryName)
                            .map(this::normalizeText)
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toSet())
                            .size();
                    long jiraMatched = employeeRecords.stream().filter(PrAnalyticsRecord::jiraMatch).count();
                    return new EmployeePrMetrics(
                            entry.getKey(),
                            employeeRecords.size(),
                            jiraMatched,
                            average(cycleTimes),
                            median(cycleTimes),
                            repositories
                    );
                })
                .sorted((left, right) -> left.employee().compareToIgnoreCase(right.employee()))
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
                    List<PrAnalyticsRecord> repositoryRecords = entry.getValue();
                    List<Double> cycleTimes = repositoryRecords.stream()
                            .map(PrAnalyticsRecord::cycleTimeDays)
                            .filter(this::isFinite)
                            .toList();
                    long jiraMatched = repositoryRecords.stream().filter(PrAnalyticsRecord::jiraMatch).count();
                    double percentage = repositoryRecords.isEmpty() ? 0d : (jiraMatched * 100.0d) / repositoryRecords.size();
                    return new RepositoryPrMetrics(entry.getKey(), repositoryRecords.size(), average(cycleTimes), percentage);
                })
                .sorted((left, right) -> left.repository().compareToIgnoreCase(right.repository()))
                .toList();
    }

    private List<CycleStartSourceMetric> buildCycleStartSources(List<PrAnalyticsRecord> mergedRecords) {
        Map<String, Long> grouped = mergedRecords.stream()
                .map(PrAnalyticsRecord::cycleStartSource)
                .map(this::normalizeText)
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

    private BitbucketRow readBitbucketRow(
            Row row,
            int jiraKeyColumn,
            int prCreatedColumn,
            int firstCommitColumn,
            int prMergedColumn) {

        return new BitbucketRow(
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "PR ID", "Pull Request ID", "ID"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "Repository Name", "Repository", "Repo"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "Project Name", "Project", "Project Key"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "Author Name", "Author", "Created By"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "Author Username", "Author User", "Username"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "State", "PR State", "Pull Request State"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "PR Title", "Title", "Summary"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "PR Description", "Description"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "Source Branch", "Source Branch Name"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "Destination Branch", "Destination Branch Name"))),
                normalizeJiraKey(getString(row.getCell(jiraKeyColumn))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "JIRA Assignee", "Jira Assignee"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "JIRA Status", "Jira Status"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "JIRA Project Name", "Jira Project Name"))),
                getString(row.getCell(findColumn(row.getSheet().getRow(0), "JIRA Sprint", "Jira Sprint"))),
                getNumeric(row.getCell(findColumn(row.getSheet().getRow(0), "JIRA Story Points", "Jira Story Points"))),
                getDateTime(row.getCell(findColumn(row.getSheet().getRow(0), "JIRA In Progress At", "Jira In Progress At", "JIRA - To Do -> In Progress"))),
                getDateTime(row.getCell(prCreatedColumn)),
                getDateTime(row.getCell(firstCommitColumn)),
                getDateTime(row.getCell(prMergedColumn))
        );
    }

    private void updateBitbucketWorkbook(Workbook workbook, JiraLookup jiraLookup) {
        Sheet sheet = workbook.getSheetAt(0);
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new IllegalStateException("Bitbucket header row not found");
        }

        int jiraKeyColumn = findColumn(header, "Jira Key", "JIRA Key", "Bitbucket Jira Key");
        int prCreatedColumn = findColumn(header, "PR Created At");
        int firstCommitColumn = findColumn(header, "First Commit Created At", "First Commit At");
        int prMergedColumn = findColumn(header, "PR Merged At");

        validateColumn(jiraKeyColumn, "Jira Key");
        validateColumn(prCreatedColumn, "PR Created At");
        validateColumn(firstCommitColumn, "First Commit Created At");
        validateColumn(prMergedColumn, "PR Merged At");

        Map<Integer, Integer> jiraToOutputColumn = new LinkedHashMap<>();
        for (int jiraColumn = 0; jiraColumn < jiraLookup.headers.size(); jiraColumn++) {
            String outputHeader = "JIRA - " + jiraLookup.headers.get(jiraColumn);
            int outputColumn = getOrCreateColumn(header, outputHeader);
            jiraToOutputColumn.put(jiraColumn, outputColumn);
        }

        int jiraMatchColumn = getOrCreateColumn(header, "Jira Match");
        int prStartColumn = getOrCreateColumn(header, "PR Start");
        int cycleTimeColumn = getOrCreateColumn(header, "Cycle Time");
        int cycleStartColumn = getOrCreateColumn(header, "BB Cycle Start");
        int cycleStartSourceColumn = getOrCreateColumn(header, "Cycle Start Source");
        int bbCycleTimeDaysColumn = getOrCreateColumn(header, "BB Cycle Time Days");

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row bitbucketRow = sheet.getRow(rowIndex);
            if (bitbucketRow == null) {
                continue;
            }

            String jiraKey = normalizeJiraKey(getString(bitbucketRow.getCell(jiraKeyColumn)));
            LocalDateTime prCreatedAt = getDateTime(bitbucketRow.getCell(prCreatedColumn));
            LocalDateTime firstCommitAt = getDateTime(bitbucketRow.getCell(firstCommitColumn));
            LocalDateTime prMergedAt = getDateTime(bitbucketRow.getCell(prMergedColumn));

            JiraData jiraData = jiraKey.isBlank() ? null : jiraLookup.dataByIssueKey.get(jiraKey);
            LocalDateTime jiraInProgressAt = jiraData == null ? null : jiraData.inProgressAt;
            LocalDateTime prStart;
            String cycleStartSource;

            if (jiraData != null) {
                setString(bitbucketRow, jiraMatchColumn, "MATCHED");
                copyAllJiraFields(bitbucketRow, jiraData, jiraToOutputColumn);

                EarliestResult earliest = findEarliest(
                        prCreatedAt, "BB PR CREATED",
                        jiraInProgressAt, "JIRA IN PROGRESS",
                        firstCommitAt, "BB FIRST COMMIT"
                );
                prStart = earliest.dateTime();
                cycleStartSource = earliest.source();
            } else {
                setString(bitbucketRow, jiraMatchColumn, "NO JIRA MATCH");
                clearJiraOutputFields(bitbucketRow, jiraToOutputColumn);

                EarliestResult earliest = findEarliest(
                        prCreatedAt, "BB PR CREATED",
                        firstCommitAt, "BB FIRST COMMIT",
                        null, null
                );
                prStart = earliest.dateTime();
                cycleStartSource = earliest.source();
            }

            setDateTime(bitbucketRow, prStartColumn, prStart);
            setDateTime(bitbucketRow, cycleStartColumn, prStart);
            setString(bitbucketRow, cycleStartSourceColumn, cycleStartSource);

            if (prStart != null && prMergedAt != null && !prMergedAt.isBefore(prStart)) {
                double cycleDays = Duration.between(prStart, prMergedAt).toSeconds() / 86400.0;
                double rounded = round1(cycleDays);
                setNumeric(bitbucketRow, cycleTimeColumn, rounded);
                setNumeric(bitbucketRow, bbCycleTimeDaysColumn, rounded);
            } else {
                clearCell(bitbucketRow, cycleTimeColumn);
                clearCell(bitbucketRow, bbCycleTimeDaysColumn);
            }
        }

        for (Integer column : jiraToOutputColumn.values()) {
            sheet.autoSizeColumn(column);
        }
        sheet.autoSizeColumn(jiraMatchColumn);
        sheet.autoSizeColumn(prStartColumn);
        sheet.autoSizeColumn(cycleTimeColumn);
        sheet.autoSizeColumn(cycleStartColumn);
        sheet.autoSizeColumn(cycleStartSourceColumn);
        sheet.autoSizeColumn(bbCycleTimeDaysColumn);
    }

    private void clearJiraOutputFields(Row row, Map<Integer, Integer> jiraToOutputColumn) {
        for (Integer outputColumn : jiraToOutputColumn.values()) {
            clearCell(row, outputColumn);
        }
    }

    private void copyAllJiraFields(Row destinationRow, JiraData jiraData, Map<Integer, Integer> jiraToOutputColumn) {
        for (Map.Entry<Integer, Integer> entry : jiraToOutputColumn.entrySet()) {
            int jiraColumn = entry.getKey();
            int outputColumn = entry.getValue();
            if (jiraColumn >= jiraData.cells.size()) {
                continue;
            }
            writeCellValue(destinationRow, outputColumn, jiraData.cells.get(jiraColumn));
        }
    }

    private void createWeeklyReport(Workbook workbook) {
        Sheet source = workbook.getSheetAt(0);
        Row sourceHeader = source.getRow(0);
        if (sourceHeader == null) {
            throw new IllegalStateException("Bitbucket header row not found");
        }

        int jiraAssigneeColumn = findColumn(sourceHeader, "JIRA - Assignee");
        int bbAuthorColumn = findColumn(sourceHeader, "Author Name");
        int mergedColumn = findColumn(sourceHeader, "PR Merged At");
        int cycleTimeColumn = findColumn(sourceHeader, "BB Cycle Time Days");

        validateColumn(mergedColumn, "PR Merged At");
        validateColumn(cycleTimeColumn, "BB Cycle Time Days");

        Set<String> people = new TreeSet<>();
        for (int rowIndex = 1; rowIndex <= source.getLastRowNum(); rowIndex++) {
            Row row = source.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String person = resolvePerson(row, jiraAssigneeColumn, bbAuthorColumn);
            if (!person.isBlank()) {
                people.add(person);
            }
        }

        List<String> employees = new ArrayList<>(people);
        Sheet existing = workbook.getSheet("Weekly Cycle Time");
        if (existing != null) {
            workbook.removeSheetAt(workbook.getSheetIndex(existing));
        }

        Sheet report = workbook.createSheet("Weekly Cycle Time");
        Row title = report.createRow(0);
        title.createCell(0).setCellValue("Cycle Time (per employee, days)");

        Row header = report.createRow(2);
        header.createCell(0).setCellValue("Week #");
        header.createCell(1).setCellValue("Week Start");
        header.createCell(2).setCellValue("Week End");
        for (int i = 0; i < employees.size(); i++) {
            header.createCell(i + 3).setCellValue(employees.get(i));
        }

        for (int weekIndex = 0; weekIndex < TOTAL_WEEKS; weekIndex++) {
            LocalDate weekStart = REPORT_START.plusDays(weekIndex * 7L);
            LocalDate weekEnd = weekStart.plusDays(6);
            Row reportRow = report.createRow(weekIndex + 3);

            reportRow.createCell(0).setCellValue(weekIndex + 1);
            reportRow.createCell(1).setCellValue(weekStart.toString());
            reportRow.createCell(2).setCellValue(weekEnd.toString());

            for (int employeeIndex = 0; employeeIndex < employees.size(); employeeIndex++) {
                String employee = employees.get(employeeIndex);
                List<Double> cycles = new ArrayList<>();

                for (int rowIndex = 1; rowIndex <= source.getLastRowNum(); rowIndex++) {
                    Row sourceRow = source.getRow(rowIndex);
                    if (sourceRow == null) {
                        continue;
                    }

                    String person = resolvePerson(sourceRow, jiraAssigneeColumn, bbAuthorColumn);
                    if (!employee.equalsIgnoreCase(person)) {
                        continue;
                    }

                    LocalDateTime mergedAt = getDateTime(sourceRow.getCell(mergedColumn));
                    if (mergedAt == null) {
                        continue;
                    }

                    LocalDate mergedDate = mergedAt.toLocalDate();
                    if (mergedDate.isBefore(weekStart) || mergedDate.isAfter(weekEnd)) {
                        continue;
                    }

                    Double cycle = getNumeric(sourceRow.getCell(cycleTimeColumn));
                    if (cycle != null) {
                        cycles.add(cycle);
                    }
                }

                double weeklyAverage = cycles.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                reportRow.createCell(employeeIndex + 3).setCellValue(round1(weeklyAverage));
            }
        }

        Row overall = report.createRow(17);
        overall.createCell(2).setCellValue("Q1 Overall");
        LocalDate reportEnd = REPORT_START.plusDays(TOTAL_WEEKS * 7L - 1);

        for (int employeeIndex = 0; employeeIndex < employees.size(); employeeIndex++) {
            String employee = employees.get(employeeIndex);
            List<Double> cycles = new ArrayList<>();

            for (int rowIndex = 1; rowIndex <= source.getLastRowNum(); rowIndex++) {
                Row sourceRow = source.getRow(rowIndex);
                if (sourceRow == null) {
                    continue;
                }

                String person = resolvePerson(sourceRow, jiraAssigneeColumn, bbAuthorColumn);
                if (!employee.equalsIgnoreCase(person)) {
                    continue;
                }

                LocalDateTime mergedAt = getDateTime(sourceRow.getCell(mergedColumn));
                if (mergedAt == null) {
                    continue;
                }

                LocalDate mergedDate = mergedAt.toLocalDate();
                if (mergedDate.isBefore(REPORT_START) || mergedDate.isAfter(reportEnd)) {
                    continue;
                }

                Double cycle = getNumeric(sourceRow.getCell(cycleTimeColumn));
                if (cycle != null) {
                    cycles.add(cycle);
                }
            }

            double overallAverage = cycles.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            overall.createCell(employeeIndex + 3).setCellValue(round1(overallAverage));
        }

        for (int column = 0; column < employees.size() + 3; column++) {
            report.autoSizeColumn(column);
        }
    }

    private String resolvePerson(Row row, int jiraAssigneeColumn, int bbAuthorColumn) {
        if (jiraAssigneeColumn >= 0) {
            String jiraAssignee = getString(row.getCell(jiraAssigneeColumn));
            if (!jiraAssignee.isBlank()) {
                return jiraAssignee.trim();
            }
        }

        if (bbAuthorColumn >= 0) {
            String bbAuthor = getString(row.getCell(bbAuthorColumn));
            if (!bbAuthor.isBlank()) {
                return bbAuthor.trim();
            }
        }

        return "";
    }

    private EarliestResult findEarliest(
            LocalDateTime first, String firstSource,
            LocalDateTime second, String secondSource,
            LocalDateTime third, String thirdSource) {
        LocalDateTime earliest = null;
        String source = "";

        if (first != null) {
            earliest = first;
            source = firstSource;
        }

        if (second != null && (earliest == null || second.isBefore(earliest))) {
            earliest = second;
            source = secondSource;
        }

        if (third != null && (earliest == null || third.isBefore(earliest))) {
            earliest = third;
            source = thirdSource;
        }

        return new EarliestResult(earliest, source);
    }

    private CellValueHolder readCellValue(Cell cell) {
        if (cell == null) {
            return CellValueHolder.blank();
        }

        return switch (cell.getCellType()) {
            case STRING -> CellValueHolder.string(cell.getStringCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield CellValueHolder.date(cell.getLocalDateTimeCellValue());
                }
                yield CellValueHolder.number(cell.getNumericCellValue());
            }
            case BOOLEAN -> CellValueHolder.bool(cell.getBooleanCellValue());
            case FORMULA -> CellValueHolder.string(new DataFormatter().formatCellValue(cell));
            default -> CellValueHolder.blank();
        };
    }

    private void writeCellValue(Row row, int column, CellValueHolder holder) {
        Cell cell = getOrCreateCell(row, column);
        switch (holder.type) {
            case STRING -> cell.setCellValue(holder.stringValue);
            case NUMBER -> cell.setCellValue(holder.numberValue);
            case BOOLEAN -> cell.setCellValue(holder.booleanValue);
            case DATE -> cell.setCellValue(holder.dateValue);
            case BLANK -> cell.setBlank();
        }
    }

    private LocalDateTime getDateTime(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }

        String value = getString(cell);
        if (value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(value, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private int findColumn(Row header, String... names) {
        if (header == null) {
            return -1;
        }

        DataFormatter formatter = new DataFormatter();
        for (Cell cell : header) {
            String value = normalizeHeader(formatter.formatCellValue(cell));
            for (String name : names) {
                if (value.equals(normalizeHeader(name))) {
                    return cell.getColumnIndex();
                }
            }
        }

        return -1;
    }

    private int getOrCreateColumn(Row header, String name) {
        int existing = findColumn(header, name);
        if (existing >= 0) {
            return existing;
        }

        int newColumn = header.getLastCellNum();
        if (newColumn < 0) {
            newColumn = 0;
        }
        header.createCell(newColumn).setCellValue(name);
        return newColumn;
    }

    private void validateColumn(int column, String name) {
        if (column < 0) {
            throw new IllegalStateException("Required column not found: " + name);
        }
    }

    private String getString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return new DataFormatter().formatCellValue(cell).trim();
    }

    private Double getNumeric(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            }
            String value = getString(cell);
            if (value.isBlank()) {
                return null;
            }
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Cell getOrCreateCell(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
        }
        return cell;
    }

    private void setString(Row row, int column, String value) {
        getOrCreateCell(row, column).setCellValue(value == null ? "" : value);
    }

    private void setNumeric(Row row, int column, double value) {
        getOrCreateCell(row, column).setCellValue(value);
    }

    private void setDateTime(Row row, int column, LocalDateTime value) {
        if (value == null) {
            clearCell(row, column);
            return;
        }
        setString(row, column, value.format(OUTPUT_DATE_FORMAT));
    }

    private void clearCell(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell != null) {
            cell.setBlank();
        }
    }

    private void validateFile(MultipartFile file, String name) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private String normalizeJiraKey(String jiraKey) {
        return jiraKey == null ? "" : jiraKey.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return "";
        }
        return header.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isMerged(PrAnalyticsRecord record) {
        return "MERGED".equalsIgnoreCase(normalizeText(record.state()));
    }

    private String resolveEmployeeName(PrAnalyticsRecord record) {
        if (record.jiraMatch() && StringUtils.hasText(record.jiraAssignee())) {
            return record.jiraAssignee().trim();
        }
        if (StringUtils.hasText(record.authorName())) {
            return record.authorName().trim();
        }
        if (StringUtils.hasText(record.authorUsername())) {
            return record.authorUsername().trim();
        }
        return "Unassigned";
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private Double firstDouble(Double first, Double second) {
        if (first != null && Double.isFinite(first)) {
            return first;
        }
        if (second != null && Double.isFinite(second)) {
            return second;
        }
        return null;
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
        List<Double> sorted = values.stream()
                .filter(this::isFinite)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0d;
    }

    private boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(OUTPUT_DATE_FORMAT);
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception ignored) {
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(trimmed, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private static class JiraData {
        private String issueKey;
        private LocalDateTime inProgressAt;
        private List<CellValueHolder> cells;
    }

    private static class MonthlyAccumulator {
        private long prsMerged;
        private long matchedPrs;
        private long unmatchedPrs;
        private final List<Double> cycleTimes = new ArrayList<>();
    }

    private record BitbucketRow(
            String prId,
            String repositoryName,
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

    private record JiraLookup(List<String> headers, Map<String, JiraData> dataByIssueKey) {
    }

    private record EarliestResult(LocalDateTime dateTime, String source) {
    }

    private enum ValueType {
        STRING,
        NUMBER,
        BOOLEAN,
        DATE,
        BLANK
    }

    private static class CellValueHolder {
        private ValueType type;
        private String stringValue;
        private double numberValue;
        private boolean booleanValue;
        private LocalDateTime dateValue;

        private String asString() {
            return switch (type) {
                case STRING -> stringValue;
                case NUMBER -> Double.toString(numberValue);
                case BOOLEAN -> Boolean.toString(booleanValue);
                case DATE -> dateValue == null ? "" : dateValue.format(OUTPUT_DATE_FORMAT);
                case BLANK -> "";
            };
        }

        private Double asDouble() {
            return switch (type) {
                case NUMBER -> numberValue;
                case STRING -> {
                    try {
                        yield Double.parseDouble(stringValue);
                    } catch (Exception ignored) {
                        yield null;
                    }
                }
                case BOOLEAN, DATE, BLANK -> null;
            };
        }

        private static CellValueHolder string(String value) {
            CellValueHolder holder = new CellValueHolder();
            holder.type = ValueType.STRING;
            holder.stringValue = value == null ? "" : value;
            return holder;
        }

        private static CellValueHolder number(double value) {
            CellValueHolder holder = new CellValueHolder();
            holder.type = ValueType.NUMBER;
            holder.numberValue = value;
            return holder;
        }

        private static CellValueHolder bool(boolean value) {
            CellValueHolder holder = new CellValueHolder();
            holder.type = ValueType.BOOLEAN;
            holder.booleanValue = value;
            return holder;
        }

        private static CellValueHolder date(LocalDateTime value) {
            CellValueHolder holder = new CellValueHolder();
            holder.type = ValueType.DATE;
            holder.dateValue = value;
            return holder;
        }

        private static CellValueHolder blank() {
            CellValueHolder holder = new CellValueHolder();
            holder.type = ValueType.BLANK;
            return holder;
        }
    }
}
