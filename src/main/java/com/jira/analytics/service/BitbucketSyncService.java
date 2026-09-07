package com.jira.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jira.analytics.dto.BitbucketPrRecord;
import com.jira.analytics.dto.BitbucketSyncRequest;
import com.jira.analytics.dto.BitbucketSyncResult;
import com.jira.analytics.dto.ProjectOption;
import com.jira.analytics.dto.SyncError;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BitbucketSyncService {

    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");
    private static final List<String> REVIEW_ACTIONS = List.of("APPROVED", "COMMENTED", "REVIEWED", "ADDED_COMMENT");

    private final ProjectJdbcRepository projectRepository;
    private final SsoUserIdJdbcRepository ssoUserIdRepository;
    private final BitbucketUserLookupClient bitbucketUserLookupClient;
    private final BitbucketApiClient bitbucketApiClient;
    private final BitbucketPrJdbcRepository bitbucketPrJdbcRepository;
    private final JiraIssueLookupRepository jiraIssueLookupRepository;

    public BitbucketSyncService(
            ProjectJdbcRepository projectRepository,
            SsoUserIdJdbcRepository ssoUserIdRepository,
            BitbucketUserLookupClient bitbucketUserLookupClient,
            BitbucketApiClient bitbucketApiClient,
            BitbucketPrJdbcRepository bitbucketPrJdbcRepository,
            JiraIssueLookupRepository jiraIssueLookupRepository
    ) {
        this.projectRepository = projectRepository;
        this.ssoUserIdRepository = ssoUserIdRepository;
        this.bitbucketUserLookupClient = bitbucketUserLookupClient;
        this.bitbucketApiClient = bitbucketApiClient;
        this.bitbucketPrJdbcRepository = bitbucketPrJdbcRepository;
        this.jiraIssueLookupRepository = jiraIssueLookupRepository;
    }

    public List<ProjectOption> findProjects() {
        return projectRepository.findAll();
    }

    public List<String> findProjectSsos(Long projectId) {
        validateProject(projectId);
        return projectRepository.findSsosByProjectId(projectId);
    }

    public BitbucketSyncResult sync(BitbucketSyncRequest request) {
        ProjectOption project = validateProject(request == null ? null : request.projectId());
        String fromDate = normalizeDate(request == null ? null : request.fromDate(), "fromDate");
        String toDate = normalizeDate(request == null ? null : request.toDate(), "toDate");
        List<String> ssos = normalizeSsos(request == null ? null : request.ssos());
        if (ssos.isEmpty()) {
            throw new IllegalStateException("Select at least one SSO before syncing Bitbucket data.");
        }

        OffsetDateTime syncedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<SyncError> errors = new ArrayList<>();
        Map<String, String> ssoToUserId = new LinkedHashMap<>();

        for (String sso : ssos) {
            Optional<SsoUserIdJdbcRepository.SsoUserIdMapping> existing =
                    ssoUserIdRepository.findByProjectIdAndSso(project.projectId(), sso);
            if (existing.isPresent() && StringUtils.hasText(existing.get().bitbucketUserId())) {
                ssoToUserId.put(sso, existing.get().bitbucketUserId());
                continue;
            }

            try {
                Optional<String> resolved = bitbucketUserLookupClient.resolveBitbucketUserId(sso);
                if (resolved.isEmpty()) {
                    errors.add(new SyncError(sso, "Unable to resolve Bitbucket user ID"));
                    continue;
                }
                String bitbucketUserId = resolved.get().trim();
                ssoUserIdRepository.saveOrUpdate(project.projectId(), sso, bitbucketUserId);
                ssoToUserId.put(sso, bitbucketUserId);
            } catch (Exception exception) {
                errors.add(new SyncError(sso, safeMessage(exception)));
            }
        }

        List<String> resolvedUserIds = new ArrayList<>(new LinkedHashSet<>(ssoToUserId.values()));
        int userIdsResolved = resolvedUserIds.size();
        if (resolvedUserIds.isEmpty()) {
            return new BitbucketSyncResult(
                    errors.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS",
                    project.projectId(),
                    ssos.size(),
                    0,
                    0,
                    0,
                    0,
                    errors,
                    syncedAt.toString()
            );
        }

        List<JsonNode> discoveryResults;
        try {
            discoveryResults = bitbucketApiClient.discoverPullRequests(project.projectKey(), fromDate, toDate, resolvedUserIds);
        } catch (Exception exception) {
            errors.add(new SyncError("DISCOVERY", safeMessage(exception)));
            return new BitbucketSyncResult(
                    "PARTIAL_SUCCESS",
                    project.projectId(),
                    ssos.size(),
                    userIdsResolved,
                    0,
                    0,
                    0,
                    errors,
                    syncedAt.toString()
            );
        }

        List<BitbucketPrRecord> normalized = new ArrayList<>();
        for (JsonNode discoveredPr : discoveryResults) {
            try {
                normalized.add(normalizePullRequest(project, discoveredPr, syncedAt));
            } catch (Exception exception) {
                errors.add(new SyncError(discoveryLabel(discoveredPr), safeMessage(exception)));
            }
        }

        List<BitbucketPrRecord> uniqueRecords = dedupePullRequests(normalized);
        BitbucketPrJdbcRepository.PersistedCounts persistedCounts =
                uniqueRecords.isEmpty() ? new BitbucketPrJdbcRepository.PersistedCounts(0, 0) : bitbucketPrJdbcRepository.saveOrUpdateAll(uniqueRecords);

        String status = errors.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS";
        return new BitbucketSyncResult(
                status,
                project.projectId(),
                ssos.size(),
                userIdsResolved,
                uniqueRecords.size(),
                persistedCounts.inserted(),
                persistedCounts.updated(),
                errors,
                syncedAt.toString()
        );
    }

    private ProjectOption validateProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalStateException("Select a project before syncing Bitbucket data.");
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Unknown project id: " + projectId));
    }

    private BitbucketPrRecord normalizePullRequest(ProjectOption project, JsonNode discoveredPr, OffsetDateTime syncedAt) {
        long prId = requireLong(discoveredPr, "id", "prId", "pullRequestId", "pullRequest.id");
        String repoSlug = firstText(discoveredPr, "repoSlug", "repository.slug", "repositorySlug", "repository.slugName");
        if (!StringUtils.hasText(repoSlug)) {
            throw new IllegalStateException("Missing repository slug for discovered pull request " + prId);
        }

        String repositoryName = firstText(discoveredPr, "repositoryName", "repository.name", "repoName");
        if (!StringUtils.hasText(repositoryName)) {
            repositoryName = repoSlug;
        }

        JsonNode pullRequest = fetchPullRequest(project.projectKey(), repoSlug, prId);
        List<JsonNode> commits = bitbucketApiClient.getPullRequestCommits(project.projectKey(), repoSlug, prId);
        List<JsonNode> activities = bitbucketApiClient.getPullRequestActivities(project.projectKey(), repoSlug, prId);

        String projectKey = firstNonBlank(
                firstText(pullRequest, "toRef.repository.project.key"),
                firstText(discoveredPr, "projectKey"),
                project.projectKey()
        );

        String title = firstNonBlank(
                firstText(pullRequest, "title"),
                firstText(discoveredPr, "title")
        );
        String description = firstNonBlank(
                firstText(pullRequest, "description"),
                firstText(discoveredPr, "description")
        );
        String sourceBranch = firstNonBlank(
                firstText(pullRequest, "fromRef.displayId"),
                firstText(pullRequest, "sourceBranch"),
                firstText(discoveredPr, "sourceBranch"),
                firstText(discoveredPr, "source.branch.name")
        );
        String destinationBranch = firstNonBlank(
                firstText(pullRequest, "toRef.displayId"),
                firstText(pullRequest, "destinationBranch"),
                firstText(discoveredPr, "destinationBranch"),
                firstText(discoveredPr, "destination.branch.name")
        );
        String state = firstNonBlank(
                firstText(pullRequest, "state"),
                firstText(discoveredPr, "state")
        );
        String authorName = firstNonBlank(
                firstText(pullRequest, "author.displayName"),
                firstText(pullRequest, "author.name"),
                firstText(discoveredPr, "author.displayName"),
                firstText(discoveredPr, "author.name"),
                firstText(discoveredPr, "authorName")
        );
        String authorUsername = firstNonBlank(
                firstText(pullRequest, "author.name"),
                firstText(pullRequest, "author.slug"),
                firstText(discoveredPr, "author.name"),
                firstText(discoveredPr, "author.slug"),
                firstText(discoveredPr, "authorUsername")
        );

        OffsetDateTime prCreatedAt = firstDateTime(
                pullRequest,
                discoveredPr,
                "createdDate",
                "createdAt",
                "pullRequest.createdDate"
        );
        OffsetDateTime firstCommitAt = firstCommitTimestamp(commits);
        OffsetDateTime firstReviewEngagementAt = firstReviewActivityTimestamp(activities);
        OffsetDateTime prMergedAt = firstMergedTimestamp(activities, pullRequest, state);

        String jiraKey = extractJiraKey(commits, title, description, sourceBranch);
        String jiraMappingSource = jiraKeySource(commits, title, description, sourceBranch, jiraKey);
        OffsetDateTime jiraInProgressAt = StringUtils.hasText(jiraKey)
                ? jiraIssueLookupRepository.findToDoToInProgressAt(jiraKey).orElse(null)
                : null;

        CycleStart cycleStart = determineCycleStart(firstCommitAt, prCreatedAt, jiraInProgressAt);
        Double cycleTimeDays = calculateCycleTimeDays(cycleStart.value(), prMergedAt);

        return new BitbucketPrRecord(
                null,
                project.projectId(),
                projectKey,
                repositoryName,
                repoSlug,
                prId,
                authorName,
                authorUsername,
                title,
                description,
                sourceBranch,
                destinationBranch,
                state,
                jiraKey,
                jiraMappingSource,
                prCreatedAt,
                firstCommitAt,
                firstReviewEngagementAt,
                prMergedAt,
                cycleStart.value(),
                cycleStart.source(),
                cycleTimeDays,
                syncedAt
        );
    }

    private JsonNode fetchPullRequest(String projectKey, String repoSlug, long prId) {
        return bitbucketApiClient.getPullRequest(projectKey, repoSlug, prId);
    }

    private CycleStart determineCycleStart(OffsetDateTime firstCommitAt, OffsetDateTime prCreatedAt, OffsetDateTime jiraInProgressAt) {
        List<Candidate> candidates = new ArrayList<>();
        if (firstCommitAt != null) {
            candidates.add(new Candidate(firstCommitAt, "BB FIRST COMMIT"));
        }
        if (prCreatedAt != null) {
            candidates.add(new Candidate(prCreatedAt, "BB PR CREATED"));
        }
        if (jiraInProgressAt != null) {
            candidates.add(new Candidate(jiraInProgressAt, "JIRA IN PROGRESS"));
        }
        if (candidates.isEmpty()) {
            return new CycleStart(null, null);
        }
        Candidate earliest = candidates.stream()
                .min(Comparator.comparing(Candidate::value))
                .orElseThrow();
        return new CycleStart(earliest.value(), earliest.source());
    }

    private Double calculateCycleTimeDays(OffsetDateTime cycleStart, OffsetDateTime prMergedAt) {
        if (cycleStart == null || prMergedAt == null || prMergedAt.isBefore(cycleStart)) {
            return null;
        }
        return Duration.between(cycleStart, prMergedAt).toMinutes() / 1440.0d;
    }

    private OffsetDateTime firstCommitTimestamp(List<JsonNode> commits) {
        return commits.stream()
                .map(this::extractCommitTimestamp)
                .filter(value -> value != null)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
    }

    private OffsetDateTime firstReviewActivityTimestamp(List<JsonNode> activities) {
        return activities.stream()
                .filter(activity -> {
                    String action = normalizeText(firstText(activity, "action"));
                    return REVIEW_ACTIONS.stream().anyMatch(review -> review.equalsIgnoreCase(action));
                })
                .map(this::extractActivityTimestamp)
                .filter(value -> value != null)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
    }

    private OffsetDateTime firstMergedTimestamp(List<JsonNode> activities, JsonNode pullRequest, String state) {
        OffsetDateTime mergedAt = activities.stream()
                .filter(activity -> "MERGED".equalsIgnoreCase(normalizeText(firstText(activity, "action"))))
                .map(this::extractActivityTimestamp)
                .filter(value -> value != null)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
        if (mergedAt != null) {
            return mergedAt;
        }
        if ("MERGED".equalsIgnoreCase(normalizeText(state))) {
            return firstDateTime(pullRequest, pullRequest, "closedDate", "closedAt", "pullRequest.closedDate");
        }
        return null;
    }

    private String extractJiraKey(List<JsonNode> commits, String... candidates) {
        for (JsonNode commit : commits) {
            String jiraKey = firstText(commit, "properties.jira-key", "commit.properties.jira-key", "properties['jira-key']");
            if (StringUtils.hasText(jiraKey)) {
                return jiraKey.trim();
            }
        }

        for (String candidate : candidates) {
            String jiraKey = regexExtract(candidate);
            if (StringUtils.hasText(jiraKey)) {
                return jiraKey;
            }
        }

        for (JsonNode commit : commits) {
            String message = firstText(commit, "message", "commit.message", "commit.message.value");
            String jiraKey = regexExtract(message);
            if (StringUtils.hasText(jiraKey)) {
                return jiraKey;
            }
        }

        return null;
    }

    private String jiraKeySource(List<JsonNode> commits, String title, String description, String sourceBranch, String jiraKey) {
        if (!StringUtils.hasText(jiraKey)) {
            return "NONE";
        }

        for (JsonNode commit : commits) {
            String key = firstText(commit, "properties.jira-key", "commit.properties.jira-key", "properties['jira-key']");
            if (StringUtils.hasText(key) && key.trim().equalsIgnoreCase(jiraKey)) {
                return "COMMIT_PROPERTY";
            }
        }

        if (StringUtils.hasText(regexExtract(title)) && regexExtract(title).equalsIgnoreCase(jiraKey)) {
            return "PR_TITLE";
        }
        if (StringUtils.hasText(regexExtract(description)) && regexExtract(description).equalsIgnoreCase(jiraKey)) {
            return "PR_DESCRIPTION";
        }
        if (StringUtils.hasText(regexExtract(sourceBranch)) && regexExtract(sourceBranch).equalsIgnoreCase(jiraKey)) {
            return "SOURCE_BRANCH";
        }

        for (JsonNode commit : commits) {
            String message = firstText(commit, "message", "commit.message", "commit.message.value");
            String extracted = regexExtract(message);
            if (StringUtils.hasText(extracted) && extracted.equalsIgnoreCase(jiraKey)) {
                return "COMMIT_MESSAGE";
            }
        }

        return "NONE";
    }

    private String regexExtract(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = JIRA_KEY_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private OffsetDateTime extractCommitTimestamp(JsonNode commit) {
        Long epochMillis = firstLong(commit, "committerTimestamp", "commit.committerTimestamp", "commit.committer.timestamp", "authorTimestamp", "date");
        if (epochMillis == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private OffsetDateTime extractActivityTimestamp(JsonNode activity) {
        Long epochMillis = firstLong(activity, "createdDate", "createdAt", "activity.createdDate", "activity.createdAt");
        if (epochMillis == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private OffsetDateTime firstDateTime(JsonNode primary, JsonNode secondary, String... pathCandidates) {
        Long epoch = firstLong(primary, pathCandidates);
        if (epoch == null) {
            epoch = firstLong(secondary, pathCandidates);
        }
        return epoch == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
    }

    private Long firstLong(JsonNode node, String... pathCandidates) {
        if (node == null || pathCandidates == null) {
            return null;
        }
        for (String candidate : pathCandidates) {
            JsonNode current = navigate(node, candidate);
            if (current != null && current.isNumber()) {
                return current.asLong();
            }
            if (current != null && current.isTextual()) {
                try {
                    return Long.parseLong(current.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Continue.
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... pathCandidates) {
        if (node == null || pathCandidates == null) {
            return null;
        }
        for (String candidate : pathCandidates) {
            JsonNode current = navigate(node, candidate);
            if (current != null && !current.isNull() && !current.isMissingNode()) {
                String value = current.asText(null);
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private JsonNode navigate(JsonNode node, String path) {
        if (node == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.path(segment);
        }
        return current;
    }

    private String discoveryLabel(JsonNode discoveredPr) {
        String repoSlug = firstText(discoveredPr, "repoSlug", "repository.slug", "repositorySlug");
        String prId = firstText(discoveredPr, "id", "prId", "pullRequestId", "pullRequest.id");
        if (StringUtils.hasText(repoSlug) && StringUtils.hasText(prId)) {
            return repoSlug + "#" + prId;
        }
        return "DISCOVERY";
    }

    private List<String> normalizeSsos(List<String> ssos) {
        if (ssos == null) {
            return List.of();
        }
        return ssos.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizeDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(fieldName + " must use yyyy-MM-dd format.");
        }
    }

    private long requireLong(JsonNode node, String... pathCandidates) {
        Long value = firstLong(node, pathCandidates);
        if (value == null) {
            throw new IllegalStateException("Missing pull request id.");
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (StringUtils.hasText(message)) {
            return message;
        }
        return exception.getClass().getSimpleName();
    }

    private List<BitbucketPrRecord> dedupePullRequests(List<BitbucketPrRecord> records) {
        Map<String, BitbucketPrRecord> deduped = new LinkedHashMap<>();
        for (BitbucketPrRecord record : records) {
            String key = record.projectKey() + "|" + record.repoSlug() + "|" + record.prId();
            deduped.putIfAbsent(key, record);
        }
        return new ArrayList<>(deduped.values());
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Candidate(OffsetDateTime value, String source) {
    }

    private record CycleStart(OffsetDateTime value, String source) {
    }
}
