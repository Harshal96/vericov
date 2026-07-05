package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardUploadEvent;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.annotation.JsonbProperty;
import java.io.StringReader;
import java.time.Instant;

public record DashboardUploadEventHttpResponse(
        @JsonbProperty("event_type") String eventType,
        JsonObject payload,
        @JsonbProperty("created_at") Instant createdAt) {
    public static DashboardUploadEventHttpResponse from(DashboardUploadEvent event) {
        return new DashboardUploadEventHttpResponse(event.eventType(), payload(event.payloadJson()), event.createdAt());
    }

    private static JsonObject payload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return jakarta.json.JsonValue.EMPTY_JSON_OBJECT.asJsonObject();
        }
        try (var reader = Json.createReader(new StringReader(payloadJson))) {
            return reader.readObject();
        }
    }
}
