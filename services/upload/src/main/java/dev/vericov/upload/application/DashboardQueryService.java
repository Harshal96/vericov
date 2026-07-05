package dev.vericov.upload.application;

import dev.vericov.upload.application.port.DashboardQueryRepository;
import dev.vericov.upload.application.port.TenantAuthenticator;
import dev.vericov.upload.domain.TenantPrincipal;
import java.util.Objects;

public class DashboardQueryService {
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
}
