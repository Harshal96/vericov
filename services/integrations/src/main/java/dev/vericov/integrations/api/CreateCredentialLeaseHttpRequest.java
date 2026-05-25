package dev.vericov.integrations.api;

import jakarta.json.bind.annotation.JsonbProperty;

public record CreateCredentialLeaseHttpRequest(
        @JsonbProperty("tenant_id")
        String tenantId,
        @JsonbProperty("org_id")
        String orgId,
        @JsonbProperty("credential_kind")
        String credentialKind) {
}
