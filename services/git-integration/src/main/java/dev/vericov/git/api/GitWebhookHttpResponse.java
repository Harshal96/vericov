package dev.vericov.git.api;

import dev.vericov.git.application.GitWebhookProcessingResult;

public record GitWebhookHttpResponse(
        String eventId,
        String providerKey,
        String deliveryId,
        String status) {

    public static GitWebhookHttpResponse from(GitWebhookProcessingResult result) {
        return new GitWebhookHttpResponse(
                result.eventId() == null ? null : result.eventId().toString(),
                result.providerKey(),
                result.deliveryId(),
                result.status());
    }
}
