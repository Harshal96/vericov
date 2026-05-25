package dev.vericov.git.application;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GitWebhookEventDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        UUID connectionId,
        UUID webhookEndpointId,
        String providerKey,
        String eventType,
        String deliveryId,
        boolean signatureValid,
        String payloadSha256,
        Map<String, Object> payload,
        Map<String, Object> normalizedPayload,
        String status,
        Map<String, Object> error,
        Instant receivedAt,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt) {

    public GitWebhookEventDetails {
        GitValues.requireId(id, "id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        eventType = GitValues.requireTrimmed(eventType, "event_type is required");
        deliveryId = GitValues.requireTrimmed(deliveryId, "delivery_id is required");
        payloadSha256 = GitValues.requireTrimmed(payloadSha256, "payload_sha256 is required");
        if (!payloadSha256.matches("[0-9a-f]{64}")) {
            throw new GitIntegrationException("validation_error", "payload_sha256 is invalid");
        }
        payload = immutable(payload);
        normalizedPayload = GitValues.deepCopyMap(normalizedPayload);
        status = GitValues.requireCanonical(status, "status is required");
        error = GitValues.deepCopyMap(error);
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static Map<String, Object> immutable(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
