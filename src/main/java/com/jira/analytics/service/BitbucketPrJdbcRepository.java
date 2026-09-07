package com.jira.analytics.service;

import com.jira.analytics.dto.BitbucketPrRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
public class BitbucketPrJdbcRepository {

    private static final String SELECT_COLUMNS = """
            id, project_id, project_key, repository_name, repo_slug, pr_id, author_name, author_username,
            title, description, source_branch, destination_branch, state, jira_key, jira_mapping_source,
            pr_created_at, first_commit_at, first_review_engagement_at, pr_merged_at, cycle_start,
            cycle_start_source, cycle_time_days, last_synced_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public BitbucketPrJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<BitbucketPrRecord> findByNaturalKey(String projectKey, String repoSlug, Long prId) {
        if (!StringUtils.hasText(projectKey) || !StringUtils.hasText(repoSlug) || prId == null) {
            return Optional.empty();
        }
        List<BitbucketPrRecord> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM bitbucket_pr WHERE project_key = ? AND repo_slug = ? AND pr_id = ?",
                (rs, rowNum) -> mapRow(rs),
                projectKey.trim(),
                repoSlug.trim(),
                prId
        );
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Set<BitbucketPrKey> findAllExistingKeys(Long projectId) {
        if (projectId == null) {
            return Set.of();
        }
        List<BitbucketPrKey> rows = jdbcTemplate.query(
                "SELECT project_key, repo_slug, pr_id FROM bitbucket_pr WHERE project_id = ?",
                (rs, rowNum) -> new BitbucketPrKey(
                        rs.getString("project_key"),
                        rs.getString("repo_slug"),
                        rs.getLong("pr_id")
                ),
                projectId
        );
        return new HashSet<>(rows);
    }

    @Transactional(readOnly = true)
    public List<BitbucketPrRecord> findByProjectId(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM bitbucket_pr WHERE project_id = ? ORDER BY repo_slug, pr_id",
                (rs, rowNum) -> mapRow(rs),
                projectId
        );
    }

    @Transactional(readOnly = true)
    public List<BitbucketPrRecord> findByDateRange(Long projectId, OffsetDateTime fromDate, OffsetDateTime toDate) {
        if (projectId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM bitbucket_pr "
                        + "WHERE project_id = ? "
                        + "AND (? IS NULL OR pr_created_at >= ?) "
                        + "AND (? IS NULL OR pr_created_at <= ?) "
                        + "ORDER BY repo_slug, pr_id",
                (rs, rowNum) -> mapRow(rs),
                projectId,
                fromDate,
                fromDate,
                toDate,
                toDate
        );
    }

    @Transactional(readOnly = true)
    public long countByProjectId(Long projectId) {
        if (projectId == null) {
            return 0L;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bitbucket_pr WHERE project_id = ?",
                Long.class,
                projectId
        );
        return count == null ? 0L : count;
    }

    @Transactional
    public void batchInsert(List<BitbucketPrRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO bitbucket_pr (
                    project_id, project_key, repository_name, repo_slug, pr_id, author_name, author_username,
                    title, description, source_branch, destination_branch, state, jira_key, jira_mapping_source,
                    pr_created_at, first_commit_at, first_review_engagement_at, pr_merged_at, cycle_start,
                    cycle_start_source, cycle_time_days, last_synced_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                records,
                500,
                (ps, record) -> bind(ps, record)
        );
    }

    @Transactional
    public void batchUpdate(List<BitbucketPrRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                UPDATE bitbucket_pr
                SET project_id = ?,
                    project_key = ?,
                    repository_name = ?,
                    author_name = ?,
                    author_username = ?,
                    title = ?,
                    description = ?,
                    source_branch = ?,
                    destination_branch = ?,
                    state = ?,
                    jira_key = ?,
                    jira_mapping_source = ?,
                    pr_created_at = ?,
                    first_commit_at = ?,
                    first_review_engagement_at = ?,
                    pr_merged_at = ?,
                    cycle_start = ?,
                    cycle_start_source = ?,
                    cycle_time_days = ?,
                    last_synced_at = ?
                WHERE id = ?
                """,
                records,
                500,
                (ps, record) -> {
                    ps.setLong(1, record.projectId());
                    ps.setString(2, record.projectKey());
                    ps.setString(3, record.repositoryName());
                    ps.setString(4, record.authorName());
                    ps.setString(5, record.authorUsername());
                    ps.setString(6, record.title());
                    ps.setString(7, record.description());
                    ps.setString(8, record.sourceBranch());
                    ps.setString(9, record.destinationBranch());
                    ps.setString(10, record.state());
                    ps.setString(11, record.jiraKey());
                    ps.setString(12, record.jiraMappingSource());
                    ps.setObject(13, record.prCreatedAt());
                    ps.setObject(14, record.firstCommitAt());
                    ps.setObject(15, record.firstReviewEngagementAt());
                    ps.setObject(16, record.prMergedAt());
                    ps.setObject(17, record.cycleStart());
                    ps.setString(18, record.cycleStartSource());
                    ps.setObject(19, record.cycleTimeDays());
                    ps.setObject(20, record.lastSyncedAt());
                    ps.setLong(21, record.id());
                }
        );
    }

    @Transactional
    public PersistedCounts saveOrUpdateAll(List<BitbucketPrRecord> records) {
        if (records == null || records.isEmpty()) {
            return new PersistedCounts(0, 0);
        }

        List<BitbucketPrRecord> inserts = new ArrayList<>();
        List<BitbucketPrRecord> updates = new ArrayList<>();
        for (BitbucketPrRecord record : records) {
            Optional<BitbucketPrRecord> existing = findByNaturalKey(record.projectKey(), record.repoSlug(), record.prId());
            if (existing.isPresent()) {
                updates.add(new BitbucketPrRecord(
                        existing.get().id(),
                        record.projectId(),
                        record.projectKey(),
                        record.repositoryName(),
                        record.repoSlug(),
                        record.prId(),
                        record.authorName(),
                        record.authorUsername(),
                        record.title(),
                        record.description(),
                        record.sourceBranch(),
                        record.destinationBranch(),
                        record.state(),
                        record.jiraKey(),
                        record.jiraMappingSource(),
                        record.prCreatedAt(),
                        record.firstCommitAt(),
                        record.firstReviewEngagementAt(),
                        record.prMergedAt(),
                        record.cycleStart(),
                        record.cycleStartSource(),
                        record.cycleTimeDays(),
                        record.lastSyncedAt()
                ));
            } else {
                inserts.add(record);
            }
        }

        batchInsert(inserts);
        batchUpdate(updates);
        return new PersistedCounts(inserts.size(), updates.size());
    }

    private void bind(java.sql.PreparedStatement ps, BitbucketPrRecord record) throws java.sql.SQLException {
        ps.setLong(1, record.projectId());
        ps.setString(2, record.projectKey());
        ps.setString(3, record.repositoryName());
        ps.setString(4, record.repoSlug());
        ps.setLong(5, record.prId());
        ps.setString(6, record.authorName());
        ps.setString(7, record.authorUsername());
        ps.setString(8, record.title());
        ps.setString(9, record.description());
        ps.setString(10, record.sourceBranch());
        ps.setString(11, record.destinationBranch());
        ps.setString(12, record.state());
        ps.setString(13, record.jiraKey());
        ps.setString(14, record.jiraMappingSource());
        ps.setObject(15, record.prCreatedAt());
        ps.setObject(16, record.firstCommitAt());
        ps.setObject(17, record.firstReviewEngagementAt());
        ps.setObject(18, record.prMergedAt());
        ps.setObject(19, record.cycleStart());
        ps.setString(20, record.cycleStartSource());
        ps.setObject(21, record.cycleTimeDays());
        ps.setObject(22, record.lastSyncedAt());
    }

    private BitbucketPrRecord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BitbucketPrRecord(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getString("project_key"),
                rs.getString("repository_name"),
                rs.getString("repo_slug"),
                rs.getLong("pr_id"),
                rs.getString("author_name"),
                rs.getString("author_username"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("source_branch"),
                rs.getString("destination_branch"),
                rs.getString("state"),
                rs.getString("jira_key"),
                rs.getString("jira_mapping_source"),
                rs.getObject("pr_created_at", OffsetDateTime.class),
                rs.getObject("first_commit_at", OffsetDateTime.class),
                rs.getObject("first_review_engagement_at", OffsetDateTime.class),
                rs.getObject("pr_merged_at", OffsetDateTime.class),
                rs.getObject("cycle_start", OffsetDateTime.class),
                rs.getString("cycle_start_source"),
                getOptionalDouble(rs, "cycle_time_days"),
                rs.getObject("last_synced_at", OffsetDateTime.class)
        );
    }

    private Double getOptionalDouble(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        Object value = rs.getObject(columnName);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Double.parseDouble(text);
        }
        return null;
    }

    public record BitbucketPrKey(String projectKey, String repoSlug, Long prId) {
    }

    public record PersistedCounts(int inserted, int updated) {
    }
}
