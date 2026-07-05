package dev.vericov.upload.application.port;

import dev.vericov.upload.domain.TenantPrincipal;

public interface TenantAuthenticator {
    TenantPrincipal authenticateTenant(String authorizationHeader);
}
