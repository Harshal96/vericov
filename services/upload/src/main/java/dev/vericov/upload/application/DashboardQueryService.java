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
}
