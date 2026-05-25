package dev.vericov.organization.application;

import dev.vericov.organization.application.port.OrganizationRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrganizationRepository implements OrganizationRepository {
    private final Map<UUID, OrganizationDetails> organizationsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> organizationIdsBySlug = new ConcurrentHashMap<>();
    private final Map<UUID, MembershipDetails> membershipsById = new ConcurrentHashMap<>();
    private final Map<UUID, OrganizationInvitation> invitationsById = new ConcurrentHashMap<>();
    private final Map<UUID, RepositoryDetails> repositoriesById = new ConcurrentHashMap<>();
    private final Map<UUID, RepositoryApiKeyDetails> repositoryApiKeysById = new ConcurrentHashMap<>();
    private final Map<UUID, PolicyDefaultsDetails> policyDefaultsByOrgId = new ConcurrentHashMap<>();
    private final Map<String, RepositoryConfigDetails> repositoryConfigsByOrgAndRepo = new ConcurrentHashMap<>();
    private final Map<UUID, RepositoryPolicyDetails> repositoryPoliciesById = new ConcurrentHashMap<>();
    private final Map<UUID, RepositoryGateDetails> repositoryGatesById = new ConcurrentHashMap<>();
    private final Map<String, RepositoryBadgeSettingsDetails> repositoryBadgeSettingsByOrgAndRepo = new ConcurrentHashMap<>();
    private final Map<String, CoverageBadgeCacheEntry> coverageBadgeCacheByKey = new ConcurrentHashMap<>();
    private final Map<UUID, CoverageReportSummary> coverageReportsById = new ConcurrentHashMap<>();
    private final Map<UUID, CoverageFileSummaryDetails> coverageFileSummariesById = new ConcurrentHashMap<>();
    private final Map<UUID, PullRequestDiffCoverageDetails> pullRequestDiffCoverageByReportId =
            new ConcurrentHashMap<>();
    private final Map<String, CoverageLineHitMapDetails> coverageLineHitsByRepositoryCommitAndFile =
            new ConcurrentHashMap<>();
    private final Map<UUID, GateEvaluationDetails> gateEvaluationsById = new ConcurrentHashMap<>();

    @Override
    public List<OrganizationDetails> findOrganizationsForUser(UUID userId) {
        return membershipsById.values().stream()
                .filter(membership -> membership.supabaseUserId().equals(userId))
                .filter(membership -> "active".equals(membership.status()))
                .map(membership -> organizationsById.get(membership.organizationId()))
                .filter(organization -> organization != null && !"deleted".equals(organization.status()))
                .sorted(Comparator.comparing(OrganizationDetails::name))
                .toList();
    }

    @Override
    public Optional<OrganizationDetails> findOrganizationForUser(UUID organizationId, UUID userId) {
        return findMembership(organizationId, userId)
                .filter(membership -> "active".equals(membership.status()))
                .map(membership -> organizationsById.get(membership.organizationId()))
                .filter(organization -> organization != null && !"deleted".equals(organization.status()));
    }

    @Override
    public Optional<OrganizationDetails> findById(UUID organizationId) {
        return Optional.ofNullable(organizationsById.get(organizationId));
    }

    @Override
    public boolean slugExists(String slug) {
        return organizationIdsBySlug.containsKey(slug);
    }

    @Override
    public synchronized OrganizationDetails createOrganizationWithOwner(
            OrganizationDetails organization,
            MembershipDetails ownerMembership) {
        if (organizationIdsBySlug.containsKey(organization.slug())) {
            throw new OrganizationException("conflict", "Organization slug already exists");
        }
        organizationsById.put(organization.id(), organization);
        organizationIdsBySlug.put(organization.slug(), organization.id());
        membershipsById.put(ownerMembership.id(), ownerMembership);
        return organization;
    }

    @Override
    public synchronized OrganizationDetails updateOrganization(OrganizationDetails organization) {
        OrganizationDetails current = organizationsById.get(organization.id());
        if (current == null) {
            throw new OrganizationException("not_found", "Organization not found");
        }
        if (!current.slug().equals(organization.slug())) {
            if (organizationIdsBySlug.containsKey(organization.slug())) {
                throw new OrganizationException("conflict", "Organization slug already exists");
            }
            organizationIdsBySlug.remove(current.slug());
            organizationIdsBySlug.put(organization.slug(), organization.id());
        }
        organizationsById.put(organization.id(), organization);
        return organization;
    }

    @Override
    public List<MembershipDetails> listMemberships(UUID organizationId) {
        return membershipsById.values().stream()
                .filter(membership -> membership.organizationId().equals(organizationId))
                .sorted(Comparator.comparing(MembershipDetails::createdAt))
                .toList();
    }

    @Override
    public Optional<MembershipDetails> findMembership(UUID organizationId, UUID userId) {
        return membershipsById.values().stream()
                .filter(membership -> membership.organizationId().equals(organizationId))
                .filter(membership -> membership.supabaseUserId().equals(userId))
                .findFirst();
    }

    @Override
    public Optional<MembershipDetails> findMembershipById(UUID organizationId, UUID membershipId) {
        return Optional.ofNullable(membershipsById.get(membershipId))
                .filter(membership -> membership.organizationId().equals(organizationId));
    }

    @Override
    public synchronized MembershipDetails saveMembership(MembershipDetails membership) {
        boolean duplicate = membershipsById.values().stream()
                .anyMatch(existing -> existing.organizationId().equals(membership.organizationId())
                        && existing.supabaseUserId().equals(membership.supabaseUserId()));
        if (duplicate) {
            throw new OrganizationException("conflict", "Membership already exists");
        }
        membershipsById.put(membership.id(), membership);
        return membership;
    }

    @Override
    public synchronized MembershipDetails updateMembership(MembershipDetails membership) {
        if (!membershipsById.containsKey(membership.id())) {
            throw new OrganizationException("not_found", "Membership not found");
        }
        membershipsById.put(membership.id(), membership);
        return membership;
    }

    @Override
    public List<OrganizationInvitation> listInvitations(UUID organizationId) {
        return invitationsById.values().stream()
                .filter(invitation -> invitation.organizationId().equals(organizationId))
                .sorted(Comparator.comparing(OrganizationInvitation::createdAt))
                .toList();
    }

    @Override
    public Optional<OrganizationInvitation> findInvitationById(UUID organizationId, UUID invitationId) {
        return Optional.ofNullable(invitationsById.get(invitationId))
                .filter(invitation -> invitation.organizationId().equals(organizationId));
    }

    @Override
    public Optional<OrganizationInvitation> findPendingInvitationByEmail(UUID organizationId, String email) {
        return invitationsById.values().stream()
                .filter(invitation -> invitation.organizationId().equals(organizationId))
                .filter(invitation -> invitation.email().equals(email))
                .filter(invitation -> "pending".equals(invitation.status()))
                .findFirst();
    }

    @Override
    public synchronized OrganizationInvitation saveInvitation(OrganizationInvitation invitation) {
        findPendingInvitationByEmail(invitation.organizationId(), invitation.email()).ifPresent(existing -> {
            throw new OrganizationException("conflict", "Invitation already exists for this email");
        });
        invitationsById.put(invitation.id(), invitation);
        return invitation;
    }

    @Override
    public synchronized OrganizationInvitation updateInvitation(OrganizationInvitation invitation) {
        if (!invitationsById.containsKey(invitation.id())) {
            throw new OrganizationException("not_found", "Invitation not found");
        }
        invitationsById.put(invitation.id(), invitation);
        return invitation;
    }

    @Override
    public List<RepositoryDetails> listRepositories(UUID organizationId) {
        return repositoriesById.values().stream()
                .filter(repository -> repository.organizationId().equals(organizationId))
                .sorted(Comparator.comparing(RepositoryDetails::fullName)
                        .thenComparing(RepositoryDetails::id))
                .toList();
    }

    @Override
    public Optional<RepositoryDetails> findRepository(UUID organizationId, UUID repositoryId) {
        return Optional.ofNullable(repositoriesById.get(repositoryId))
                .filter(repository -> repository.organizationId().equals(organizationId));
    }

    @Override
    public Optional<RepositoryDetails> findRepositoryByProviderIdentity(
            UUID organizationId,
            String provider,
            String providerRepositoryId) {
        return repositoriesById.values().stream()
                .filter(repository -> repository.organizationId().equals(organizationId))
                .filter(repository -> repository.provider().equals(provider))
                .filter(repository -> repository.providerRepositoryId().equals(providerRepositoryId))
                .findFirst();
    }

    @Override
    public synchronized RepositoryDetails saveRepository(RepositoryDetails repository) {
        findRepositoryByProviderIdentity(
                repository.organizationId(),
                repository.provider(),
                repository.providerRepositoryId()).ifPresent(existing -> {
                    throw new OrganizationException("conflict", "Repository already exists");
                });
        repositoriesById.put(repository.id(), repository);
        return repository;
    }

    @Override
    public synchronized RepositoryDetails updateRepository(RepositoryDetails repository) {
        if (!repositoriesById.containsKey(repository.id())) {
            throw new OrganizationException("not_found", "Repository not found");
        }
        repositoriesById.put(repository.id(), repository);
        return repository;
    }

    @Override
    public List<RepositoryApiKeyDetails> listRepositoryApiKeys(UUID repositoryId) {
        return repositoryApiKeysById.values().stream()
                .filter(apiKey -> apiKey.repositoryId().equals(repositoryId))
                .sorted(Comparator.comparing(RepositoryApiKeyDetails::createdAt)
                        .thenComparing(RepositoryApiKeyDetails::id))
                .toList();
    }

    @Override
    public Optional<RepositoryApiKeyDetails> findRepositoryApiKey(UUID repositoryId, UUID apiKeyId) {
        return Optional.ofNullable(repositoryApiKeysById.get(apiKeyId))
                .filter(apiKey -> apiKey.repositoryId().equals(repositoryId));
    }

    @Override
    public synchronized RepositoryApiKeyDetails saveRepositoryApiKey(RepositoryApiKeyDetails apiKey) {
        boolean duplicatePrefix = repositoryApiKeysById.values().stream()
                .anyMatch(existing -> existing.repositoryId().equals(apiKey.repositoryId())
                        && existing.keyPrefix().equals(apiKey.keyPrefix()));
        if (duplicatePrefix) {
            throw new OrganizationException("conflict", "Repository API key already exists");
        }
        repositoryApiKeysById.put(apiKey.id(), apiKey);
        return apiKey;
    }

    @Override
    public synchronized RepositoryApiKeyDetails updateRepositoryApiKey(RepositoryApiKeyDetails apiKey) {
        if (!repositoryApiKeysById.containsKey(apiKey.id())) {
            throw new OrganizationException("not_found", "Repository API key not found");
        }
        repositoryApiKeysById.put(apiKey.id(), apiKey);
        return apiKey;
    }

    @Override
    public Optional<PolicyDefaultsDetails> findPolicyDefaults(UUID organizationId) {
        return Optional.ofNullable(policyDefaultsByOrgId.get(organizationId));
    }

    @Override
    public synchronized PolicyDefaultsDetails savePolicyDefaults(PolicyDefaultsDetails defaults) {
        if (policyDefaultsByOrgId.containsKey(defaults.organizationId())) {
            throw new OrganizationException("conflict", "Policy defaults already exist");
        }
        policyDefaultsByOrgId.put(defaults.organizationId(), defaults);
        return defaults;
    }

    @Override
    public synchronized PolicyDefaultsDetails updatePolicyDefaults(PolicyDefaultsDetails defaults) {
        if (!policyDefaultsByOrgId.containsKey(defaults.organizationId())) {
            throw new OrganizationException("not_found", "Policy defaults not found");
        }
        policyDefaultsByOrgId.put(defaults.organizationId(), defaults);
        return defaults;
    }

    @Override
    public Optional<RepositoryConfigDetails> findRepositoryConfig(UUID organizationId, UUID repositoryId) {
        return Optional.ofNullable(repositoryConfigsByOrgAndRepo.get(orgRepositoryKey(organizationId, repositoryId)));
    }

    @Override
    public synchronized RepositoryConfigDetails saveRepositoryConfig(RepositoryConfigDetails config) {
        String key = orgRepositoryKey(config.organizationId(), config.repositoryId());
        if (repositoryConfigsByOrgAndRepo.containsKey(key)) {
            throw new OrganizationException("conflict", "Repository config already exists");
        }
        repositoryConfigsByOrgAndRepo.put(key, config);
        return config;
    }

    @Override
    public synchronized RepositoryConfigDetails updateRepositoryConfig(RepositoryConfigDetails config) {
        String key = orgRepositoryKey(config.organizationId(), config.repositoryId());
        if (!repositoryConfigsByOrgAndRepo.containsKey(key)) {
            throw new OrganizationException("not_found", "Repository config not found");
        }
        repositoryConfigsByOrgAndRepo.put(key, config);
        return config;
    }

    @Override
    public List<RepositoryPolicyDetails> listRepositoryPolicies(UUID organizationId, UUID repositoryId) {
        return repositoryPoliciesById.values().stream()
                .filter(policy -> policy.organizationId().equals(organizationId))
                .filter(policy -> policy.repositoryId().equals(repositoryId))
                .sorted(Comparator.comparingInt(RepositoryPolicyDetails::priority)
                        .thenComparing(RepositoryPolicyDetails::name)
                        .thenComparing(RepositoryPolicyDetails::id))
                .toList();
    }

    @Override
    public Optional<RepositoryPolicyDetails> findRepositoryPolicy(UUID organizationId, UUID repositoryId, UUID policyId) {
        return Optional.ofNullable(repositoryPoliciesById.get(policyId))
                .filter(policy -> policy.organizationId().equals(organizationId))
                .filter(policy -> policy.repositoryId().equals(repositoryId));
    }

    @Override
    public synchronized RepositoryPolicyDetails saveRepositoryPolicy(RepositoryPolicyDetails policy) {
        repositoryPoliciesById.put(policy.id(), policy);
        return policy;
    }

    @Override
    public synchronized RepositoryPolicyDetails updateRepositoryPolicy(RepositoryPolicyDetails policy) {
        if (!repositoryPoliciesById.containsKey(policy.id())) {
            throw new OrganizationException("not_found", "Repository policy not found");
        }
        repositoryPoliciesById.put(policy.id(), policy);
        return policy;
    }

    @Override
    public List<RepositoryGateDetails> listRepositoryGates(UUID organizationId, UUID repositoryId) {
        return repositoryGatesById.values().stream()
                .filter(gate -> gate.organizationId().equals(organizationId))
                .filter(gate -> gate.repositoryId().equals(repositoryId))
                .sorted(Comparator.comparing(RepositoryGateDetails::name)
                        .thenComparing(RepositoryGateDetails::id))
                .toList();
    }

    @Override
    public synchronized void replaceRepositoryGates(
            UUID organizationId,
            UUID repositoryId,
            List<RepositoryGateDetails> gates) {
        repositoryGatesById.entrySet().removeIf(entry -> entry.getValue().organizationId().equals(organizationId)
                && entry.getValue().repositoryId().equals(repositoryId));
        gates.forEach(gate -> repositoryGatesById.put(gate.id(), gate));
    }

    @Override
    public Optional<RepositoryBadgeSettingsDetails> findRepositoryBadgeSettings(
            UUID organizationId,
            UUID repositoryId) {
        return Optional.ofNullable(repositoryBadgeSettingsByOrgAndRepo.get(orgRepositoryKey(organizationId, repositoryId)));
    }

    @Override
    public synchronized RepositoryBadgeSettingsDetails saveRepositoryBadgeSettings(
            RepositoryBadgeSettingsDetails settings) {
        String key = orgRepositoryKey(settings.organizationId(), settings.repositoryId());
        if (repositoryBadgeSettingsByOrgAndRepo.containsKey(key)) {
            throw new OrganizationException("conflict", "Repository badge settings already exist");
        }
        repositoryBadgeSettingsByOrgAndRepo.put(key, settings);
        return settings;
    }

    @Override
    public synchronized RepositoryBadgeSettingsDetails updateRepositoryBadgeSettings(
            RepositoryBadgeSettingsDetails settings) {
        String key = orgRepositoryKey(settings.organizationId(), settings.repositoryId());
        if (!repositoryBadgeSettingsByOrgAndRepo.containsKey(key)) {
            throw new OrganizationException("not_found", "Repository badge settings not found");
        }
        repositoryBadgeSettingsByOrgAndRepo.put(key, settings);
        return settings;
    }

    @Override
    public Optional<CoverageBadgeCacheEntry> findFreshCoverageBadgeCache(
            UUID organizationId,
            UUID repositoryId,
            String cacheScope,
            String branch,
            String metric,
            Instant settingsUpdatedAt,
            Instant now) {
        CoverageBadgeCacheEntry entry = coverageBadgeCacheByKey.get(badgeCacheKey(
                organizationId,
                repositoryId,
                cacheScope,
                branch,
                metric));
        if (entry == null
                || !entry.settingsUpdatedAt().equals(settingsUpdatedAt)
                || !entry.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public synchronized CoverageBadgeCacheEntry upsertCoverageBadgeCache(CoverageBadgeCacheEntry entry) {
        coverageBadgeCacheByKey.put(badgeCacheKey(
                entry.organizationId(),
                entry.repositoryId(),
                entry.cacheScope(),
                entry.branch(),
                entry.metric()), entry);
        return entry;
    }

    @Override
    public synchronized void deleteCoverageBadgeCache(UUID organizationId, UUID repositoryId) {
        coverageBadgeCacheByKey.entrySet().removeIf(entry -> entry.getValue().organizationId().equals(organizationId)
                && entry.getValue().repositoryId().equals(repositoryId));
    }

    @Override
    public Optional<CoverageReportSummary> findLatestCoverageReport(UUID repositoryId, String branch) {
        return coverageReportsById.values().stream()
                .filter(report -> report.repositoryId().equals(repositoryId))
                .filter(report -> report.branch().equals(branch))
                .sorted(Comparator.comparing(CoverageReportSummary::createdAt)
                        .thenComparing(CoverageReportSummary::id)
                        .reversed())
                .findFirst();
    }

    @Override
    public Optional<CoverageReportSummary> findCoverageReportByCommit(UUID repositoryId, String commitSha) {
        return coverageReportsById.values().stream()
                .filter(report -> report.repositoryId().equals(repositoryId))
                .filter(report -> report.commitSha().equals(commitSha))
                .sorted(Comparator.comparing(CoverageReportSummary::createdAt)
                        .thenComparing(CoverageReportSummary::id)
                        .reversed())
                .findFirst();
    }

    @Override
    public Optional<CoverageReportSummary> findLatestPullRequestCoverageReport(UUID repositoryId, int pullRequestNumber) {
        return coverageReportsById.values().stream()
                .filter(report -> report.repositoryId().equals(repositoryId))
                .filter(report -> report.pullRequestNumber() != null && report.pullRequestNumber() == pullRequestNumber)
                .sorted(Comparator.comparing(CoverageReportSummary::createdAt)
                        .thenComparing(CoverageReportSummary::id)
                        .reversed())
                .findFirst();
    }

    @Override
    public List<CoverageReportSummary> listCoverageReports(UUID repositoryId, String branch, int limit) {
        return coverageReportsById.values().stream()
                .filter(report -> report.repositoryId().equals(repositoryId))
                .filter(report -> report.branch().equals(branch))
                .sorted(Comparator.comparing(CoverageReportSummary::createdAt)
                        .thenComparing(CoverageReportSummary::id))
                .limit(limit)
                .toList();
    }

    @Override
    public List<CoverageFileSummaryDetails> listCoverageFileSummaries(UUID coverageReportId, int limit) {
        return coverageFileSummariesById.values().stream()
                .filter(summary -> summary.coverageReportId().equals(coverageReportId))
                .sorted(Comparator.comparing(CoverageFileSummaryDetails::filePath)
                        .thenComparing(CoverageFileSummaryDetails::id))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<PullRequestDiffCoverageDetails> findPullRequestDiffCoverage(
            UUID coverageReportId,
            boolean includeLines) {
        return Optional.ofNullable(pullRequestDiffCoverageByReportId.get(coverageReportId))
                .map(details -> includeLines ? details : withoutDiffLines(details));
    }

    @Override
    public CoverageLineHitMapDetails findCoverageLineHits(
            UUID repositoryId,
            String commitSha,
            String filePath) {
        CoverageReportSummary report = findCoverageReportByCommit(repositoryId, commitSha).orElse(null);
        CoverageLineHitMapDetails details = coverageLineHitsByRepositoryCommitAndFile.get(
                lineHitsKey(repositoryId, commitSha, filePath));
        if (details != null) {
            return details;
        }
        return new CoverageLineHitMapDetails(
                repositoryId,
                report == null ? null : report.id(),
                commitSha,
                Map.of(filePath, Map.of()));
    }

    @Override
    public List<GateEvaluationDetails> listGateEvaluations(
            UUID organizationId,
            UUID repositoryId,
            String branch,
            String status,
            int limit) {
        return gateEvaluationsById.values().stream()
                .filter(evaluation -> evaluation.organizationId().equals(organizationId))
                .filter(evaluation -> evaluation.repositoryId().equals(repositoryId))
                .filter(evaluation -> branch == null || evaluation.branch().equals(branch))
                .filter(evaluation -> status == null || evaluation.status().equals(status))
                .sorted(Comparator.comparing(GateEvaluationDetails::evaluatedAt)
                        .thenComparing(GateEvaluationDetails::id)
                        .reversed())
                .limit(limit)
                .toList();
    }

    public synchronized CoverageReportSummary saveCoverageReport(CoverageReportSummary report) {
        coverageReportsById.put(report.id(), report);
        return report;
    }

    public synchronized CoverageFileSummaryDetails saveCoverageFileSummary(CoverageFileSummaryDetails summary) {
        coverageFileSummariesById.put(summary.id(), summary);
        return summary;
    }

    public synchronized PullRequestDiffCoverageDetails savePullRequestDiffCoverage(
            PullRequestDiffCoverageDetails details) {
        pullRequestDiffCoverageByReportId.put(details.coverageReportId(), details);
        return details;
    }

    public synchronized CoverageLineHitMapDetails saveCoverageLineHits(CoverageLineHitMapDetails details) {
        details.files().forEach((filePath, hits) -> coverageLineHitsByRepositoryCommitAndFile.put(
                lineHitsKey(details.repositoryId(), details.commitSha(), filePath),
                new CoverageLineHitMapDetails(
                        details.repositoryId(),
                        details.coverageReportId(),
                        details.commitSha(),
                        Map.of(filePath, hits))));
        return details;
    }

    public synchronized GateEvaluationDetails saveGateEvaluation(GateEvaluationDetails evaluation) {
        gateEvaluationsById.put(evaluation.id(), evaluation);
        return evaluation;
    }

    private static String orgRepositoryKey(UUID organizationId, UUID repositoryId) {
        return organizationId + ":" + repositoryId;
    }

    private static String badgeCacheKey(
            UUID organizationId,
            UUID repositoryId,
            String cacheScope,
            String branch,
            String metric) {
        return organizationId + ":" + repositoryId + ":" + cacheScope + ":" + branch + ":" + metric;
    }

    private static String lineHitsKey(UUID repositoryId, String commitSha, String filePath) {
        return repositoryId + ":" + commitSha + ":" + filePath;
    }

    private static PullRequestDiffCoverageDetails withoutDiffLines(PullRequestDiffCoverageDetails details) {
        return new PullRequestDiffCoverageDetails(
                details.id(),
                details.coverageReportId(),
                details.baseSha(),
                details.headSha(),
                details.status(),
                details.patchLine(),
                details.newlyMissedLineCount(),
                details.lostCoverageLineCount(),
                details.files().stream()
                        .map(file -> new DiffCoverageFileDetails(
                                file.filePath(),
                                file.oldFilePath(),
                                file.changeStatus(),
                                file.patchLine(),
                                file.newlyMissedLineCount(),
                                file.lostCoverageLineCount(),
                                List.of()))
                        .toList(),
                details.createdAt(),
                details.updatedAt());
    }
}
