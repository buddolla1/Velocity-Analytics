package com.jira.analytics.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
public class SsoUserIdJdbcRepository {

    private static final String SELECT_COLUMNS = "id, project_id, sso, bitbucket_user_id, last_synced_at";

    private final JdbcTemplate jdbcTemplate;

    public SsoUserIdJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<SsoUserIdMapping> findByProjectIdAndSso(Long projectId, String sso) {
        if (projectId == null || !StringUtils.hasText(sso)) {
            return Optional.empty();
        }
        List<SsoUserIdMapping> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM sso_userid WHERE project_id = ? AND sso = ?",
                (rs, rowNum) -> new SsoUserIdMapping(
                        rs.getLong("id"),
                        rs.getLong("project_id"),
                        rs.getString("sso"),
                        rs.getString("bitbucket_user_id"),
                        rs.getObject("last_synced_at", OffsetDateTime.class)
                ),
                projectId,
                sso.trim()
        );
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<SsoUserIdMapping> findByProjectId(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM sso_userid WHERE project_id = ? ORDER BY sso",
                (rs, rowNum) -> new SsoUserIdMapping(
                        rs.getLong("id"),
                        rs.getLong("project_id"),
                        rs.getString("sso"),
                        rs.getString("bitbucket_user_id"),
                        rs.getObject("last_synced_at", OffsetDateTime.class)
                ),
                projectId
        );
    }

    @Transactional
    public void batchInsert(List<SsoUserIdMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO sso_userid (project_id, sso, bitbucket_user_id, last_synced_at)
                VALUES (?, ?, ?, ?)
                """,
                mappings,
                500,
                (ps, mapping) -> {
                    ps.setLong(1, mapping.projectId());
                    ps.setString(2, mapping.sso());
                    ps.setString(3, mapping.bitbucketUserId());
                    ps.setObject(4, mapping.lastSyncedAt());
                }
        );
    }

    @Transactional
    public void batchUpdate(List<SsoUserIdMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                UPDATE sso_userid
                SET bitbucket_user_id = ?, last_synced_at = ?
                WHERE id = ?
                """,
                mappings,
                500,
                (ps, mapping) -> {
                    ps.setString(1, mapping.bitbucketUserId());
                    ps.setObject(2, mapping.lastSyncedAt());
                    ps.setLong(3, mapping.id());
                }
        );
    }

    @Transactional
    public SsoUserIdMapping saveOrUpdate(Long projectId, String sso, String bitbucketUserId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id is required.");
        }
        if (!StringUtils.hasText(sso)) {
            throw new IllegalArgumentException("SSO is required.");
        }
        if (!StringUtils.hasText(bitbucketUserId)) {
            throw new IllegalArgumentException("Bitbucket user id is required.");
        }

        OffsetDateTime syncedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<SsoUserIdMapping> existing = findByProjectIdAndSso(projectId, sso);
        if (existing.isPresent()) {
            SsoUserIdMapping current = existing.get();
            if (!bitbucketUserId.trim().equals(current.bitbucketUserId())) {
                jdbcTemplate.update(
                        """
                        UPDATE sso_userid
                        SET bitbucket_user_id = ?, last_synced_at = ?
                        WHERE id = ?
                        """,
                        bitbucketUserId.trim(),
                        syncedAt,
                        current.id()
                );
                return new SsoUserIdMapping(current.id(), projectId, sso.trim(), bitbucketUserId.trim(), syncedAt);
            }

            jdbcTemplate.update(
                    "UPDATE sso_userid SET last_synced_at = ? WHERE id = ?",
                    syncedAt,
                    current.id()
            );
            return new SsoUserIdMapping(current.id(), projectId, sso.trim(), current.bitbucketUserId(), syncedAt);
        }

        jdbcTemplate.update(
                """
                INSERT INTO sso_userid (project_id, sso, bitbucket_user_id, last_synced_at)
                VALUES (?, ?, ?, ?)
                """,
                projectId,
                sso.trim(),
                bitbucketUserId.trim(),
                syncedAt
        );
        return findByProjectIdAndSso(projectId, sso).orElseThrow();
    }

    public record SsoUserIdMapping(
            Long id,
            Long projectId,
            String sso,
            String bitbucketUserId,
            OffsetDateTime lastSyncedAt
    ) {
    }
}
