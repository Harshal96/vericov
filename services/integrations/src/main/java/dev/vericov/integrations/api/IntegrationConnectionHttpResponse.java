package dev.vericov.integrations.api;

import dev.vericov.integrations.application.IntegrationConnectionDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationConnectionHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("integration_type")
        String integrationType,
        @JsonbProperty("display_name")
        String displayName,
        @JsonbProperty("external_account_id")
        String externalAccountId,
        @JsonbProperty("external_account_name")
        String externalAccountName,
        String status,
        Map<String, Object> config,
        @JsonbProperty("created_by")
        UUID createdBy,
        @JsonbProperty("last_verified_at")
        Instant lastVerifiedAt,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static IntegrationConnectionHttpResponse from(IntegrationConnectionDetails connection) {
        return new IntegrationConnectionHttpResponse(
                connection.id(),
                connection.tenantId(),
                connection.orgId(),
                connection.providerKey(),
                connection.integrationType(),
                connection.displayName(),
                connection.externalAccountId(),
                connection.externalAccountName(),
                connection.status(),
                connection.config(),
                connection.createdBy(),
                connection.lastVerifiedAt(),
                connection.createdAt(),
                connection.updatedAt());
    }
}
