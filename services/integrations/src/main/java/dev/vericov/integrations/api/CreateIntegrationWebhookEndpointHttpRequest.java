package dev.vericov.integrations.api;

import dev.vericov.integrations.application.CreateIntegrationWebhookEndpointCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateIntegrationWebhookEndpointHttpRequest(
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("external_webhook_id")
        String externalWebhookId,
        @JsonbProperty("endpoint_url")
        String endpointUrl,
        @JsonbProperty("event_types")
        List<String> eventTypes,
        @JsonbProperty("signing_secret_ref")
        String signingSecretRef,
        Map<String, Object> config) {

    public CreateIntegrationWebhookEndpointCommand toCommand(UUID requesterUserId, UUID connectionId) {
        return new CreateIntegrationWebhookEndpointCommand(
                requesterUserId,
                tenantId,
                orgId,
                connectionId,
                providerKey,
                externalWebhookId,
                endpointUrl,
                eventTypes,
                signingSecretRef,
                config);
    }
}
