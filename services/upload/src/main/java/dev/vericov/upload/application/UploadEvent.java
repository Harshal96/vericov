package dev.vericov.upload.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UploadEvent(
        UUID eventId,
        UUID tenantId,
        UUID uploadId,
        String eventType,
        Map<String, String> payload,
        Instant createdAt) {

    public UploadEvent {
        payload = Map.copyOf(payload == null ? Map.of() : payload);
    }
}
