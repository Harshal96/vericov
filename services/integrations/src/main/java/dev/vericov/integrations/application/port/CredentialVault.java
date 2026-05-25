package dev.vericov.integrations.application.port;

import dev.vericov.integrations.application.CredentialLease;
import java.util.UUID;

public interface CredentialVault {
    String store(UUID tenantId, UUID connectionId, String credentialKind, char[] secret);

    CredentialLease lease(UUID tenantId, UUID connectionId, String secretRef, String requestedBy);

    void revoke(UUID tenantId, String secretRef);
}
