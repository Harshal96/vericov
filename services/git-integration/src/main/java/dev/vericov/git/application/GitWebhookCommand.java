package dev.vericov.git.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public record GitWebhookCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        UUID connectionId,
        UUID webhookEndpointId,
        String providerKey,
        String eventType,
        String deliveryId,
        String signature,
        byte[] payload,
        Instant receivedAt) {

    public GitWebhookCommand {
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        eventType = GitValues.requireTrimmed(eventType, "event_type is required");
        deliveryId = GitValues.requireTrimmed(deliveryId, "delivery_id is required");
        signature = GitValues.requireTrimmed(signature, "signature is required");
        if (payload == null || payload.length == 0) {
            throw new GitIntegrationException("validation_error", "payload is required");
        }
        payload = Arrays.copyOf(payload, payload.length);
        if (receivedAt == null) {
            throw new GitIntegrationException("validation_error", "received_at is required");
        }
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
