package dev.vericov.upload.application;

import dev.vericov.upload.application.port.DashboardQueryRepository;
import dev.vericov.upload.application.port.TenantAuthenticator;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.domain.TenantPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DashboardQueryService {
    private static final int DEFAULT_SPARKLINE_POINTS = 20;
    private static final int MAX_SPARKLINE_POINTS = 60;
    private static final int DEFAULT_TREND_POINTS = 60;
    private static final int MAX_TREND_POINTS = 200;
    private static final int DEFAULT_RECENT_REPORTS = 30;
    private static final int MAX_RECENT_REPORTS = 100;
    private static final int DEFAULT_TENANT_GAPS = 100;
    private static final int DEFAULT_REPOSITORY_GAPS = 200;
    private static final int MAX_GAPS = 500;
    private static final int DEFAULT_GATE_EVALUATIONS = 60;
    private static final int MAX_GATE_EVALUATIONS = 200;
    private static final int MAX_FILE_PATH_LENGTH = 1024;
    private static final int DEFAULT_PULL_REQUEST_DIFFS = 40;
    private static final int MAX_PULL_REQUEST_DIFFS = 100;
    private static final int DEFAULT_TEST_RUNS = 60;
    private static final int MAX_TEST_RUNS = 200;
    private static final int DEFAULT_UPLOADS = 50;
    private static final int MAX_UPLOADS = 200;
    private static final int DEFAULT_MANIFEST_LIMIT = 100;
    private static final int MAX_MANIFEST_LIMIT = 500;

    private final TenantAuthenticator authenticator;
    private final DashboardQueryRepository repository;
    private final UploadRepository uploadRepository;

    public DashboardQueryService(
            TenantAuthenticator authenticator,
            DashboardQueryRepository repository,
            UploadRepository uploadRepository) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.uploadRepository = Objects.requireNonNull(uploadRepository, "uploadRepository");
    }

    public DashboardOverview overview(String authorizationHeader) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.overview(principal.tenantId());
    }

    public List<DashboardRepositoryOverview> repositories(String authorizationHeader) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.repositories(principal.tenantId());
    }

    public Map<UUID, List<BigDecimal>> sparklines(String authorizationHeader, Integer perRepository) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.sparklines(principal.tenantId(), normalizedSparklineLimit(perRepository));
    }

    public DashboardRepository repository(String authorizationHeader, UUID repositoryId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.repository(principal.tenantId(), repositoryId)
                .orElseThrow(() -> new InvalidUploadException("repo_not_found", "Repository not found"));
    }

    public List<DashboardTrendPoint> trend(
            String authorizationHeader, UUID repositoryId, String branch, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.trend(principal.tenantId(), repositoryId, normalizedBranch(branch), normalizedLimit(
                limit, DEFAULT_TREND_POINTS, MAX_TREND_POINTS));
    }

    public List<DashboardReportListItem> reports(String authorizationHeader, UUID repositoryId, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.reports(principal.tenantId(), repositoryId, normalizedLimit(
                limit, DEFAULT_RECENT_REPORTS, MAX_RECENT_REPORTS));
    }

    public DashboardReportDetails report(String authorizationHeader, UUID reportId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.report(principal.tenantId(), reportId)
                .orElseThrow(() -> new InvalidUploadException("report_not_found", "Report not found"));
    }

    public FileSummaryResult reportFiles(String authorizationHeader, UUID reportId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureReportExists(principal.tenantId(), reportId);
        return new FileSummaryResult(repository.reportFiles(principal.tenantId(), reportId), "");
    }

    public List<DashboardFileLineHit> reportLineHits(String authorizationHeader, UUID reportId, String filePath) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureReportExists(principal.tenantId(), reportId);
        String normalizedFilePath = normalizedRequiredFilePath(filePath);
        if (!repository.reportFileExists(principal.tenantId(), reportId, normalizedFilePath)) {
            String basename = normalizedFilePath.contains("/")
                    ? normalizedFilePath.substring(normalizedFilePath.lastIndexOf('/') + 1)
                    : normalizedFilePath;
            throw new FileNotFoundQueryException(
                    "No file found at path " + normalizedFilePath + " in the report",
                    repository.similarReportFilePaths(principal.tenantId(), reportId, basename, 5));
        }
        return repository.reportLineHits(principal.tenantId(), reportId, normalizedFilePath);
    }

    public List<DashboardComponentRollup> reportComponents(String authorizationHeader, UUID reportId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureReportExists(principal.tenantId(), reportId);
        return repository.reportComponents(principal.tenantId(), reportId);
    }

    public List<DashboardGapFinding> gaps(String authorizationHeader, String status, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.gaps(
                principal.tenantId(),
                normalizedOptionalStatus(status),
                normalizedLimit(limit, DEFAULT_TENANT_GAPS, MAX_GAPS));
    }

    public List<DashboardGapFinding> repositoryGaps(
            String authorizationHeader, UUID repositoryId, String status, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureRepositoryExists(principal.tenantId(), repositoryId);
        return repository.repositoryGaps(
                principal.tenantId(),
                repositoryId,
                normalizedOptionalStatus(status),
                normalizedLimit(limit, DEFAULT_REPOSITORY_GAPS, MAX_GAPS));
    }

    public DashboardGapCounts repositoryGapCounts(String authorizationHeader, UUID repositoryId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureRepositoryExists(principal.tenantId(), repositoryId);
        return repository.repositoryGapCounts(principal.tenantId(), repositoryId);
    }

    public CoverageGapManifest manifest(
            String authorizationHeader,
            UUID repositoryId,
            String ref,
            Integer pullRequestNumber,
            String nextAction,
            String minRiskLevel,
            Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureRepositoryExists(principal.tenantId(), repositoryId);
        // Repository ownership is already confirmed against the tenant above, so it is safe to
        // delegate to the same repository-scoped UploadRepository the agent-facing endpoint uses —
        // this is what keeps the dashboard manifest byte-for-byte identical to the agent one.
        ResolvedCoverageRef resolved = pullRequestNumber != null
                ? uploadRepository.resolveCoverageRefForPullRequest(repositoryId, pullRequestNumber)
                        .orElseThrow(() -> new InvalidUploadException(
                                "pull_request_not_found",
                                "No diff-bearing coverage report found for pull request " + pullRequestNumber
                                        + ". Uploads must include a diff artifact."))
                : uploadRepository.resolveCoverageRef(repositoryId, ref)
                        .orElseThrow(() -> new InvalidUploadException(
                                "ref_not_found",
                                "No complete coverage report found for ref " + describeRef(ref)));
        CoverageReportDetails report = uploadRepository.coverageReportFor(resolved.uploadId())
                .orElseThrow(() -> new InvalidUploadException("ref_not_found", "Coverage report not found"));
        RepositoryInfo repositoryInfo = uploadRepository.repositoryInfo(repositoryId)
                .orElse(new RepositoryInfo(null, null));
        PatchCoverageDetails patch = report.pullRequestNumber() == null
                ? null
                : uploadRepository.patchForPullRequest(repositoryId, report.pullRequestNumber()).orElse(null);
        List<CoverageGateEvaluationDetails> failedGates = uploadRepository.gatesFor(resolved.reportId()).stream()
                .filter(gate -> "failed".equals(gate.status()) && gate.blocking())
                .toList();
        int effectiveLimit = normalizedLimit(limit, DEFAULT_MANIFEST_LIMIT, MAX_MANIFEST_LIMIT);
        List<CoverageGapManifestEntry> page = uploadRepository.gapManifestEntries(
                resolved.reportId(), nextAction, minRiskLevel, effectiveLimit + 1, 0);
        boolean truncated = page.size() > effectiveLimit;
        List<CoverageGapManifestEntry> entries = truncated ? page.subList(0, effectiveLimit) : page;
        return new CoverageGapManifest(
                1,
                Instant.now(),
                repositoryInfo,
                resolved,
                report.pullRequestNumber(),
                report.gateStatus(),
                report.configSha256(),
                patch,
                failedGates,
                entries,
                truncated);
    }

    public List<DashboardGateConfig> gateConfigs(String authorizationHeader, UUID repositoryId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureRepositoryExists(principal.tenantId(), repositoryId);
        return repository.gateConfigs(principal.tenantId(), repositoryId);
    }

    public List<DashboardGateEvaluation> repositoryGateEvaluations(
            String authorizationHeader, UUID repositoryId, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureRepositoryExists(principal.tenantId(), repositoryId);
        return repository.repositoryGateEvaluations(
                principal.tenantId(),
                repositoryId,
                normalizedLimit(limit, DEFAULT_GATE_EVALUATIONS, MAX_GATE_EVALUATIONS));
    }

    public List<DashboardGateEvaluation> reportGates(String authorizationHeader, UUID reportId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureReportExists(principal.tenantId(), reportId);
        return repository.reportGates(principal.tenantId(), reportId);
    }

    public List<DashboardPullRequestDiff> pullRequestDiffs(
            String authorizationHeader, UUID repositoryId, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureRepositoryExists(principal.tenantId(), repositoryId);
        return repository.pullRequestDiffs(
                principal.tenantId(),
                repositoryId,
                normalizedLimit(limit, DEFAULT_PULL_REQUEST_DIFFS, MAX_PULL_REQUEST_DIFFS));
    }

    public List<DashboardTestRun> testRuns(String authorizationHeader, UUID repositoryId, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        ensureRepositoryExists(principal.tenantId(), repositoryId);
        return repository.testRuns(
                principal.tenantId(),
                repositoryId,
                normalizedLimit(limit, DEFAULT_TEST_RUNS, MAX_TEST_RUNS));
    }

    public DashboardPullRequestDiffDetails pullRequestDiff(String authorizationHeader, UUID diffId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.pullRequestDiff(principal.tenantId(), diffId)
                .orElseThrow(() -> new InvalidUploadException(
                        "pull_request_not_found", "Pull request diff not found"));
    }

    public List<DashboardUploadListItem> uploads(
            String authorizationHeader, UUID repositoryId, String status, Integer limit) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.listUploads(
                principal.tenantId(),
                repositoryId,
                normalizedOptionalStatus(status),
                normalizedLimit(limit, DEFAULT_UPLOADS, MAX_UPLOADS));
    }

    public DashboardUploadDetails uploadDetails(String authorizationHeader, UUID uploadId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        return repository.uploadDetails(principal.tenantId(), uploadId)
                .orElseThrow(() -> new InvalidUploadException("upload_not_found", "Upload not found"));
    }

    public List<DashboardUploadArtifact> uploadArtifacts(String authorizationHeader, UUID uploadId) {
        TenantPrincipal principal = authenticator.authenticateTenant(authorizationHeader);
        if (repository.uploadDetails(principal.tenantId(), uploadId).isEmpty()) {
            throw new InvalidUploadException("upload_not_found", "Upload not found");
        }
        return repository.uploadArtifacts(principal.tenantId(), uploadId);
    }

    private void ensureReportExists(UUID tenantId, UUID reportId) {
        if (repository.report(tenantId, reportId).isEmpty()) {
            throw new InvalidUploadException("report_not_found", "Report not found");
        }
    }

    private void ensureRepositoryExists(UUID tenantId, UUID repositoryId) {
        if (repository.repository(tenantId, repositoryId).isEmpty()) {
            throw new InvalidUploadException("repo_not_found", "Repository not found");
        }
    }

    private static int normalizedSparklineLimit(Integer perRepository) {
        return normalizedLimit(perRepository, DEFAULT_SPARKLINE_POINTS, MAX_SPARKLINE_POINTS);
    }

    private static int normalizedLimit(Integer limit, int defaultValue, int maxValue) {
        if (limit == null) {
            return defaultValue;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, maxValue);
    }

    private static String normalizedBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return null;
        }
        return branch;
    }

    private static String describeRef(String ref) {
        return ref == null || ref.isBlank() ? "<default branch>" : ref;
    }

    private static String normalizedOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status;
    }

    private static String normalizedRequiredFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new InvalidUploadException("missing_file_path", "file_path is required");
        }
        if (filePath.length() > MAX_FILE_PATH_LENGTH) {
            throw new InvalidUploadException("validation_error", "file_path exceeds " + MAX_FILE_PATH_LENGTH + " characters");
        }
        return filePath;
    }

    public record FileSummaryResult(List<DashboardFileSummary> files, String nextCursor) {
        public FileSummaryResult {
            files = List.copyOf(files == null ? List.of() : files);
        }
    }

    public static final class FileNotFoundQueryException extends InvalidUploadException {
        private final List<String> didYouMean;

        public FileNotFoundQueryException(String message, List<String> didYouMean) {
            super("file_not_found", message);
            this.didYouMean = List.copyOf(didYouMean == null ? List.of() : didYouMean);
        }

        public List<String> didYouMean() {
            return didYouMean;
        }
    }
}
