package dev.vericov.git.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PublishedGitEvent(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        UUID connectionId,
        String providerKey,
        String eventType,
        String deliveryId,
        Map<String, Object> normalizedPayload,
        Instant receivedAt,
        Instant processedAt) {

    public PublishedGitEvent {
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        eventType = GitValues.requireTrimmed(eventType, "event_type is required");
        deliveryId = GitValues.requireTrimmed(deliveryId, "delivery_id is required");
        normalizedPayload = GitValues.deepCopyMap(normalizedPayload);
        if (receivedAt == null) {
            throw new IllegalArgumentException("receivedAt is required");
        }
        if (processedAt == null) {
            throw new IllegalArgumentException("processedAt is required");
        }
    }
}
