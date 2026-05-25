package dev.vericov.git.application;

import java.util.UUID;

public record GitWebhookProcessingResult(
        UUID eventId,
        String providerKey,
        String deliveryId,
        String status) {

    public GitWebhookProcessingResult {
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        deliveryId = GitValues.requireTrimmed(deliveryId, "delivery_id is required");
        status = GitValues.requireCanonical(status, "status is required");
    }
}
