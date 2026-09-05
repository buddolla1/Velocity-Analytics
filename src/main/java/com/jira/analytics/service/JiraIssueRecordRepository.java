package com.jira.analytics.service;

import com.jira.analytics.exception.InvalidExcelException;
import com.syf.jirametrics.model.JiraIssueRecord;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
public class JiraIssueRecordRepository {

    private static final String SELECT_COLUMNS = """
            id, issue_id, issue_key, issue_type, status, project_key, project_name, summary,
            story_points, sprint, assignee, sso, resolved_at, month_number, month_name,
            to_do_to_in_progress_at, in_progress_to_validation_at, validation_to_done_at,
            jira_cycle_time_days, jira_updated_at, last_synced_at
            """;

    private static final String INSERT_SQL = """
            INSERT INTO jira_issue_record (
                issue_id, issue_key, issue_type, status, project_key, project_name, summary,
                story_points, sprint, assignee, sso, resolved_at, month_number, month_name,
                to_do_to_in_progress_at, in_progress_to_validation_at, validation_to_done_at,
                jira_cycle_time_days, jira_updated_at, last_synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE jira_issue_record
            SET issue_key = ?,
                issue_type = ?,
                status = ?,
                project_key = ?,
                project_name = ?,
                summary = ?,
                story_points = ?,
                sprint = ?,
                assignee = ?,
                sso = ?,
                resolved_at = ?,
                month_number = ?,
                month_name = ?,
                to_do_to_in_progress_at = ?,
                in_progress_to_validation_at = ?,
                validation_to_done_at = ?,
                jira_cycle_time_days = ?,
                jira_updated_at = ?,
                last_synced_at = ?
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<JiraIssueRecord> rowMapper = (rs, rowNum) -> new JiraIssueRecord(
            rs.getLong("id"),
            rs.getString("issue_id"),
            rs.getString("issue_key"),
            rs.getString("issue_type"),
            rs.getString("status"),
            rs.getString("project_key"),
            rs.getString("project_name"),
            rs.getString("summary"),
            getOptionalDouble(rs, "story_points"),
            rs.getString("sprint"),
            rs.getString("assignee"),
            rs.getString("sso"),
            getOptionalOffsetDateTime(rs, "resolved_at"),
            getOptionalInteger(rs, "month_number"),
            rs.getString("month_name"),
            getOptionalOffsetDateTime(rs, "to_do_to_in_progress_at"),
            getOptionalOffsetDateTime(rs, "in_progress_to_validation_at"),
            getOptionalOffsetDateTime(rs, "validation_to_done_at"),
            getOptionalDouble(rs, "jira_cycle_time_days"),
            getOptionalOffsetDateTime(rs, "jira_updated_at"),
            getOptionalOffsetDateTime(rs, "last_synced_at")
    );

    public JiraIssueRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<JiraIssueRecord> findAll() {
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM jira_issue_record ORDER BY issue_key", rowMapper);
    }

    @Transactional(readOnly = true)
    public Optional<JiraIssueRecord> findByIssueId(String issueId) {
        if (!StringUtils.hasText(issueId)) {
            return Optional.empty();
        }
        try {
            JiraIssueRecord record = jdbcTemplate.queryForObject(
                    "SELECT " + SELECT_COLUMNS + " FROM jira_issue_record WHERE issue_id = ?",
                    rowMapper,
                    issueId.trim()
            );
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public List<JiraIssueRecord> saveAll(List<JiraIssueRecord> records) {
        List<JiraIssueRecord> saved = new ArrayList<>(records.size());
        for (JiraIssueRecord record : records) {
            if (!StringUtils.hasText(record.issueId()) || !StringUtils.hasText(record.issueKey())) {
                throw new InvalidExcelException("Jira records must include issue id and issue key.");
            }
            saved.add(save(record));
        }
        return saved;
    }

    @Transactional
    public JiraIssueRecord save(JiraIssueRecord record) {
        Optional<JiraIssueRecord> existing = findByIssueId(record.issueId());
        if (existing.isPresent()) {
            update(existing.get().id(), record);
            return new JiraIssueRecord(
                    existing.get().id(),
                    record.issueId(),
                    record.issueKey(),
                    record.issueType(),
                    record.status(),
                    record.projectKey(),
                    record.projectName(),
                    record.summary(),
                    record.storyPoints(),
                    record.sprint(),
                    record.assignee(),
                    record.sso(),
                    record.resolvedAt(),
                    record.monthNumber(),
                    record.monthName(),
                    record.toDoToInProgressAt(),
                    record.inProgressToValidationAt(),
                    record.validationToDoneAt(),
                    record.jiraCycleTimeDays(),
                    record.jiraUpdatedAt(),
                    record.lastSyncedAt()
            );
        }

        Long id = insert(record);
        return new JiraIssueRecord(
                id,
                record.issueId(),
                record.issueKey(),
                record.issueType(),
                record.status(),
                record.projectKey(),
                record.projectName(),
                record.summary(),
                record.storyPoints(),
                record.sprint(),
                record.assignee(),
                record.sso(),
                record.resolvedAt(),
                record.monthNumber(),
                record.monthName(),
                record.toDoToInProgressAt(),
                record.inProgressToValidationAt(),
                record.validationToDoneAt(),
                record.jiraCycleTimeDays(),
                record.jiraUpdatedAt(),
                record.lastSyncedAt()
        );
    }

    private Long insert(JiraIssueRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            bindValues(statement, record, null);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to capture generated Jira issue id.");
        }
        return key.longValue();
    }

    private void update(Long id, JiraIssueRecord record) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(UPDATE_SQL);
            bindValues(statement, record, id);
            return statement;
        });
    }

    private void bindValues(PreparedStatement statement, JiraIssueRecord record, Long id) throws SQLException {
        int index = 1;
        statement.setString(index++, record.issueId());
        statement.setString(index++, record.issueKey());
        statement.setString(index++, record.issueType());
        statement.setString(index++, record.status());
        statement.setString(index++, record.projectKey());
        statement.setString(index++, record.projectName());
        statement.setString(index++, record.summary());
        setOptionalDouble(statement, index++, record.storyPoints());
        statement.setString(index++, record.sprint());
        statement.setString(index++, record.assignee());
        statement.setString(index++, record.sso());
        setOptionalOffsetDateTime(statement, index++, record.resolvedAt());
        setOptionalInteger(statement, index++, record.monthNumber());
        statement.setString(index++, record.monthName());
        setOptionalOffsetDateTime(statement, index++, record.toDoToInProgressAt());
        setOptionalOffsetDateTime(statement, index++, record.inProgressToValidationAt());
        setOptionalOffsetDateTime(statement, index++, record.validationToDoneAt());
        setOptionalDouble(statement, index++, record.jiraCycleTimeDays());
        setOptionalOffsetDateTime(statement, index++, record.jiraUpdatedAt());
        setOptionalOffsetDateTime(statement, index++, record.lastSyncedAt());
        if (id != null) {
            statement.setLong(index, id);
        }
    }

    private static Double getOptionalDouble(java.sql.ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer getOptionalInteger(java.sql.ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static OffsetDateTime getOptionalOffsetDateTime(java.sql.ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static void setOptionalDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null || !Double.isFinite(value)) {
            statement.setNull(index, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    private static void setOptionalInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setOptionalOffsetDateTime(PreparedStatement statement, int index, OffsetDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value.toInstant()));
        }
    }
}
