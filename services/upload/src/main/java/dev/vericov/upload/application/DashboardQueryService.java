package dev.vericov.upload.application;

import dev.vericov.upload.application.port.DashboardQueryRepository;
import dev.vericov.upload.application.port.TenantAuthenticator;
import dev.vericov.upload.domain.TenantPrincipal;
import java.math.BigDecimal;
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
    private static final int MAX_FILE_PATH_LENGTH = 1024;

    private final TenantAuthenticator authenticator;
    private final DashboardQueryRepository repository;

    public DashboardQueryService(TenantAuthenticator authenticator, DashboardQueryRepository repository) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.repository = Objects.requireNonNull(repository, "repository");
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

    private void ensureReportExists(UUID tenantId, UUID reportId) {
        if (repository.report(tenantId, reportId).isEmpty()) {
            throw new InvalidUploadException("report_not_found", "Report not found");
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
