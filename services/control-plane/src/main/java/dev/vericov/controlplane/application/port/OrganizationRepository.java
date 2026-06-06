package dev.vericov.controlplane.application.port;

import dev.vericov.controlplane.application.CoverageBadgeCacheEntry;
import dev.vericov.controlplane.application.CoverageDebtDetails;
import dev.vericov.controlplane.application.CoverageDebtEventDetails;
import dev.vericov.controlplane.application.CoverageFileSummaryDetails;
import dev.vericov.controlplane.application.CoverageGapFindingDetails;
import dev.vericov.controlplane.application.CoverageLineHitMapDetails;
import dev.vericov.controlplane.application.CoverageReportSummary;
import dev.vericov.controlplane.application.GateEvaluationDetails;
import dev.vericov.controlplane.application.MembershipDetails;
import dev.vericov.controlplane.application.OrganizationDetails;
import dev.vericov.controlplane.application.PolicyDefaultsDetails;
import dev.vericov.controlplane.application.PullRequestDiffCoverageDetails;
import dev.vericov.controlplane.application.RepositoryApiKeyDetails;
import dev.vericov.controlplane.application.RepositoryBadgeSettingsDetails;
import dev.vericov.controlplane.application.RepositoryConfigDetails;
import dev.vericov.controlplane.application.RepositoryComponentDetails;
import dev.vericov.controlplane.application.RepositoryDetails;
import dev.vericov.controlplane.application.RepositoryGateDetails;
import dev.vericov.controlplane.application.RepositoryOwnerRuleDetails;
import dev.vericov.controlplane.application.RepositoryPackageNodeDetails;
import dev.vericov.controlplane.application.RepositoryPolicyDetails;
import dev.vericov.controlplane.application.TestRunDetails;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    Optional<OrganizationDetails> findById(UUID organizationId);

    Optional<MembershipDetails> findMembership(UUID organizationId, UUID userId);

    List<RepositoryDetails> listRepositories(UUID organizationId);

    Optional<RepositoryDetails> findRepository(UUID organizationId, UUID repositoryId);

    Optional<RepositoryDetails> findRepositoryById(UUID repositoryId);

    Optional<RepositoryDetails> findRepositoryByProviderIdentity(
            UUID organizationId,
            String provider,
            String providerRepositoryId);

    RepositoryDetails saveRepository(RepositoryDetails repository);

    RepositoryDetails updateRepository(RepositoryDetails repository);

    List<RepositoryComponentDetails> listRepositoryComponents(UUID organizationId, UUID repositoryId);

    Optional<RepositoryComponentDetails> findRepositoryComponent(UUID organizationId, UUID repositoryId, UUID componentId);

    RepositoryComponentDetails saveRepositoryComponent(RepositoryComponentDetails component);

    RepositoryComponentDetails updateRepositoryComponent(RepositoryComponentDetails component);

    List<RepositoryOwnerRuleDetails> listRepositoryOwnerRules(UUID organizationId, UUID repositoryId);

    void replaceRepositoryOwnerRules(UUID organizationId, UUID repositoryId, List<RepositoryOwnerRuleDetails> ownerRules);

    List<RepositoryPackageNodeDetails> listRepositoryPackageNodes(UUID organizationId, UUID repositoryId);

    void replaceRepositoryPackageNodes(UUID organizationId, UUID repositoryId, List<RepositoryPackageNodeDetails> packageNodes);

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

    List<CoverageReportSummary> listCoverageReports(
            UUID repositoryId,
            String branch,
            Instant from,
            Instant to,
            int limit);

    List<CoverageFileSummaryDetails> listCoverageFileSummaries(UUID coverageReportId, int limit);

    Optional<PullRequestDiffCoverageDetails> findPullRequestDiffCoverage(UUID coverageReportId, boolean includeLines);

    CoverageLineHitMapDetails findCoverageLineHits(UUID repositoryId, String commitSha, String filePath);

    List<TestRunDetails> listTestRuns(UUID repositoryId, String commitSha, int limit);

    List<GateEvaluationDetails> listGateEvaluations(UUID organizationId, UUID repositoryId, String branch, String status, int limit);

    Optional<CoverageGapFindingDetails> findCoverageGap(UUID repositoryId, UUID gapId);

    List<CoverageGapFindingDetails> listCoverageGaps(
            UUID organizationId,
            UUID repositoryId,
            String commitSha,
            Integer pullRequestNumber,
            UUID componentId,
            String owner,
            String minRisk,
            String riskLevel,
            String status,
            String reasonCode,
            boolean includeDebt,
            int limit);

    Optional<CoverageDebtDetails> findCoverageDebt(UUID repositoryId, UUID debtId);

    List<CoverageDebtDetails> listCoverageDebts(
            UUID repositoryId,
            String status,
            String owner,
            String riskLevel,
            UUID componentId,
            Instant expiresBefore,
            boolean includeExpired,
            UUID sourceGapId,
            int limit);

    CoverageDebtDetails saveCoverageDebt(CoverageDebtDetails debtItem);

    CoverageDebtDetails updateCoverageDebt(CoverageDebtDetails debtItem);

    void saveCoverageDebtEvent(CoverageDebtEventDetails event);

    List<CoverageDebtEventDetails> listCoverageDebtEvents(UUID debtItemId);
}
