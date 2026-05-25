package dev.vericov.integrations.api;

import dev.vericov.integrations.application.CreateIntegrationConnectionCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;
import java.util.UUID;

public record CreateIntegrationConnectionHttpRequest(
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("display_name")
        String displayName,
        @JsonbProperty("external_account_id")
        String externalAccountId,
        @JsonbProperty("external_account_name")
        String externalAccountName,
        Map<String, Object> config) {

    public CreateIntegrationConnectionCommand toCommand(UUID requesterUserId, UUID orgId) {
        return new CreateIntegrationConnectionCommand(
                requesterUserId,
                tenantId,
                orgId,
                providerKey,
                displayName,
                externalAccountId,
                externalAccountName,
                config);
    }
}
