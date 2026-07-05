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

    private static int normalizedSparklineLimit(Integer perRepository) {
        if (perRepository == null) {
            return DEFAULT_SPARKLINE_POINTS;
        }
        if (perRepository < 1) {
            return 1;
        }
        return Math.min(perRepository, MAX_SPARKLINE_POINTS);
    }
}
