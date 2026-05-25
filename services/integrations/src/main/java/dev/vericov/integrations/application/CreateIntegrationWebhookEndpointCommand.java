package dev.vericov.integrations.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateIntegrationWebhookEndpointCommand(
        UUID requesterUserId,
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        String providerKey,
        String externalWebhookId,
        String endpointUrl,
        List<String> eventTypes,
        String signingSecretRef,
        Map<String, Object> config) {
}
