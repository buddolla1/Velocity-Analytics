package com.jira.analytics.service;

import com.jira.analytics.dto.JiraIssue;
import com.jira.analytics.exception.InvalidExcelException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class JiraExcelParser {

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "issuetype",
            "issuekey",
            "status",
            "projectkey",
            "projectname"
    );

    private final DataFormatter dataFormatter = new DataFormatter(Locale.US);

    public List<JiraIssue> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidExcelException("No file was uploaded.");
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
                throw new InvalidExcelException("Missing Jira header row.");
            }

            Map<String, Integer> headerMap = buildHeaderMap(headerRow, evaluator);
            validateRequiredColumns(headerMap);

            List<JiraIssue> issues = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                JiraIssue issue = readIssue(row, headerMap, evaluator);
                if (StringUtils.hasText(issue.issueKey()) || StringUtils.hasText(issue.summary())) {
                    issues.add(issue);
                }
            }
            return issues;
        } catch (IOException exception) {
            throw new InvalidExcelException("Invalid Excel file.");
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidExcelException) {
                throw exception;
            }
            throw new InvalidExcelException("Invalid Excel file.");
        }
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

    private void validateRequiredColumns(Map<String, Integer> headerMap) {
        Set<String> missing = new TreeSet<>();
        for (String column : REQUIRED_COLUMNS) {
            if (!headerMap.containsKey(column)) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidExcelException("Missing required Jira columns: " + String.join(", ", missing));
        }
    }

    private JiraIssue readIssue(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator) {
        return new JiraIssue(
                readString(row, headers, evaluator, "issuetype"),
                readString(row, headers, evaluator, "issuekey"),
                readOptionalString(row, headers, evaluator, "issueid"),
                readString(row, headers, evaluator, "status"),
                readString(row, headers, evaluator, "projectkey"),
                readString(row, headers, evaluator, "projectname"),
                readOptionalString(row, headers, evaluator, "summary"),
                readOptionalDouble(row, headers, evaluator, "storypoints"),
                readOptionalString(row, headers, evaluator, "sprint"),
                readOptionalString(row, headers, evaluator, "assignee"),
                readOptionalString(row, headers, evaluator, "sso"),
                readOptionalDateTimeString(row, headers, evaluator, "resolved"),
                readOptionalInteger(row, headers, evaluator, "monthnumber"),
                readOptionalString(row, headers, evaluator, "monthname"),
                readOptionalString(row, headers, evaluator, "todotoinprogress"),
                readOptionalString(row, headers, evaluator, "validation"),
                readOptionalString(row, headers, evaluator, "done"),
                readOptionalDouble(row, headers, evaluator, "cycletimedays")
        );
    }

    private String readString(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String header) {
        String value = readOptionalString(row, headers, evaluator, header);
        if (!StringUtils.hasText(value)) {
            throw new InvalidExcelException("Missing required value for column: " + header);
        }
        return value;
    }

    private String readOptionalString(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String header) {
        Cell cell = getCell(row, headers, header);
        if (cell == null) {
            return null;
        }
        String value = readCellAsString(cell, evaluator);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Double readOptionalDouble(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String header) {
        String value = readOptionalString(row, headers, evaluator, header);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer readOptionalInteger(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String header) {
        String value = readOptionalString(row, headers, evaluator, header);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String readOptionalDateTimeString(Row row, Map<String, Integer> headers, FormulaEvaluator evaluator, String header) {
        Cell cell = getCell(row, headers, header);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(cell.getDateCellValue().toInstant(), ZoneId.systemDefault());
            return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        String value = readCellAsString(cell, evaluator);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Cell getCell(Row row, Map<String, Integer> headers, String header) {
        Integer index = headers.get(header);
        return index == null ? null : row.getCell(index);
    }

    private String readCellAsString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return dataFormatter.formatCellValue(cell, evaluator).trim();
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && StringUtils.hasText(dataFormatter.formatCellValue(cell).trim())) {
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
}
