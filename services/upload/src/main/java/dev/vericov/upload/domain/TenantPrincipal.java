package dev.vericov.upload.domain;

import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(
        UUID tenantId,
        UUID externalOrgId,
        String userId,
        Set<String> roles,
        Set<String> scopes) {
    public TenantPrincipal {
        roles = Set.copyOf(roles == null ? Set.of() : roles);
        scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
    }
}
