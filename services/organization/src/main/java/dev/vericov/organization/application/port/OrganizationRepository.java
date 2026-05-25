package dev.vericov.organization.application.port;

import dev.vericov.organization.application.MembershipDetails;
import dev.vericov.organization.application.OrganizationInvitation;
import dev.vericov.organization.application.OrganizationDetails;
import dev.vericov.organization.application.PolicyDefaultsDetails;
import dev.vericov.organization.application.CoverageBadgeCacheEntry;
import dev.vericov.organization.application.CoverageReportSummary;
import dev.vericov.organization.application.CoverageFileSummaryDetails;
import dev.vericov.organization.application.CoverageLineHitMapDetails;
import dev.vericov.organization.application.GateEvaluationDetails;
import dev.vericov.organization.application.PullRequestDiffCoverageDetails;
import dev.vericov.organization.application.RepositoryBadgeSettingsDetails;
import dev.vericov.organization.application.RepositoryApiKeyDetails;
import dev.vericov.organization.application.RepositoryConfigDetails;
import dev.vericov.organization.application.RepositoryDetails;
import dev.vericov.organization.application.RepositoryGateDetails;
import dev.vericov.organization.application.RepositoryPolicyDetails;
import dev.vericov.organization.application.TestRunDetails;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    List<OrganizationDetails> findOrganizationsForUser(UUID userId);

    Optional<OrganizationDetails> findOrganizationForUser(UUID organizationId, UUID userId);

    Optional<OrganizationDetails> findById(UUID organizationId);

    boolean slugExists(String slug);

    OrganizationDetails createOrganizationWithOwner(OrganizationDetails organization, MembershipDetails ownerMembership);

    OrganizationDetails updateOrganization(OrganizationDetails organization);

    List<MembershipDetails> listMemberships(UUID organizationId);

    Optional<MembershipDetails> findMembership(UUID organizationId, UUID userId);

    Optional<MembershipDetails> findMembershipById(UUID organizationId, UUID membershipId);

    MembershipDetails saveMembership(MembershipDetails membership);

    MembershipDetails updateMembership(MembershipDetails membership);

    List<OrganizationInvitation> listInvitations(UUID organizationId);

    Optional<OrganizationInvitation> findInvitationById(UUID organizationId, UUID invitationId);

    Optional<OrganizationInvitation> findPendingInvitationByEmail(UUID organizationId, String email);

    OrganizationInvitation saveInvitation(OrganizationInvitation invitation);

    OrganizationInvitation updateInvitation(OrganizationInvitation invitation);

    List<RepositoryDetails> listRepositories(UUID organizationId);

    Optional<RepositoryDetails> findRepository(UUID organizationId, UUID repositoryId);

    Optional<RepositoryDetails> findRepositoryByProviderIdentity(
            UUID organizationId,
            String provider,
            String providerRepositoryId);

    RepositoryDetails saveRepository(RepositoryDetails repository);

    RepositoryDetails updateRepository(RepositoryDetails repository);

    List<RepositoryApiKeyDetails> listRepositoryApiKeys(UUID repositoryId);

    Optional<RepositoryApiKeyDetails> findRepositoryApiKey(UUID repositoryId, UUID apiKeyId);

    RepositoryApiKeyDetails saveRepositoryApiKey(RepositoryApiKeyDetails apiKey);

    RepositoryApiKeyDetails updateRepositoryApiKey(RepositoryApiKeyDetails apiKey);

    Optional<PolicyDefaultsDetails> findPolicyDefaults(UUID organizationId);

    PolicyDefaultsDetails savePolicyDefaults(PolicyDefaultsDetails defaults);

    PolicyDefaultsDetails updatePolicyDefaults(PolicyDefaultsDetails defaults);

    Optional<RepositoryConfigDetails> findRepositoryConfig(UUID organizationId, UUID repositoryId);

    RepositoryConfigDetails saveRepositoryConfig(RepositoryConfigDetails config);

    RepositoryConfigDetails updateRepositoryConfig(RepositoryConfigDetails config);

    List<RepositoryPolicyDetails> listRepositoryPolicies(UUID organizationId, UUID repositoryId);

    Optional<RepositoryPolicyDetails> findRepositoryPolicy(UUID organizationId, UUID repositoryId, UUID policyId);

    RepositoryPolicyDetails saveRepositoryPolicy(RepositoryPolicyDetails policy);

    RepositoryPolicyDetails updateRepositoryPolicy(RepositoryPolicyDetails policy);

    List<RepositoryGateDetails> listRepositoryGates(UUID organizationId, UUID repositoryId);

    void replaceRepositoryGates(UUID organizationId, UUID repositoryId, List<RepositoryGateDetails> gates);

    Optional<RepositoryBadgeSettingsDetails> findRepositoryBadgeSettings(UUID organizationId, UUID repositoryId);

    RepositoryBadgeSettingsDetails saveRepositoryBadgeSettings(RepositoryBadgeSettingsDetails settings);

    RepositoryBadgeSettingsDetails updateRepositoryBadgeSettings(RepositoryBadgeSettingsDetails settings);

    Optional<CoverageBadgeCacheEntry> findFreshCoverageBadgeCache(
            UUID organizationId,
            UUID repositoryId,
            String cacheScope,
            String branch,
            String metric,
            Instant settingsUpdatedAt,
            Instant now);

    CoverageBadgeCacheEntry upsertCoverageBadgeCache(CoverageBadgeCacheEntry entry);

    void deleteCoverageBadgeCache(UUID organizationId, UUID repositoryId);

    Optional<CoverageReportSummary> findLatestCoverageReport(UUID repositoryId, String branch);

    Optional<CoverageReportSummary> findCoverageReportByCommit(UUID repositoryId, String commitSha);

    Optional<CoverageReportSummary> findLatestPullRequestCoverageReport(UUID repositoryId, int pullRequestNumber);

    List<CoverageReportSummary> listCoverageReports(UUID repositoryId, String branch, int limit);

    List<CoverageFileSummaryDetails> listCoverageFileSummaries(UUID coverageReportId, int limit);

    Optional<PullRequestDiffCoverageDetails> findPullRequestDiffCoverage(UUID coverageReportId, boolean includeLines);

    CoverageLineHitMapDetails findCoverageLineHits(UUID repositoryId, String commitSha, String filePath);

    List<TestRunDetails> listTestRuns(UUID repositoryId, String commitSha, int limit);

    List<GateEvaluationDetails> listGateEvaluations(UUID organizationId, UUID repositoryId, String branch, String status, int limit);
}
