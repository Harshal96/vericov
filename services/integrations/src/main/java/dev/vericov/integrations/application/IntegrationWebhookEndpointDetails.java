package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record IntegrationWebhookEndpointDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        String providerKey,
        String externalWebhookId,
        String endpointUrl,
        List<String> eventTypes,
        String status,
        String signingSecretRef,
        Map<String, Object> config,
        Map<String, Object> lastDelivery,
        Instant lastDeliveredAt,
        Instant createdAt,
        Instant updatedAt) {

    public IntegrationWebhookEndpointDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(connectionId, "connectionId");
        providerKey = IntegrationConfigValues.requireCanonical(providerKey, "providerKey");
        externalWebhookId = externalWebhookId == null || externalWebhookId.trim().isBlank()
                ? null
                : externalWebhookId.trim();
        endpointUrl = IntegrationConfigValues.requireTrimmed(endpointUrl, "endpointUrl");
        eventTypes = eventTypes == null
                ? List.of()
                : eventTypes.stream()
                        .map(eventType -> IntegrationConfigValues.requireTrimmed(eventType, "eventType"))
                        .distinct()
                        .toList();
        if (eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes must not be empty");
        }
        status = IntegrationConfigValues.requireCanonical(status, "status");
        signingSecretRef = IntegrationConfigValues.requireTrimmed(signingSecretRef, "signingSecretRef");
        config = IntegrationConfigValues.deepCopyMap(config);
        lastDelivery = IntegrationConfigValues.deepCopyMap(lastDelivery);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
