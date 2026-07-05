package dev.vericov.upload.application;

import java.time.Instant;

public record DashboardUploadEvent(String eventType, String payloadJson, Instant createdAt) {
}
