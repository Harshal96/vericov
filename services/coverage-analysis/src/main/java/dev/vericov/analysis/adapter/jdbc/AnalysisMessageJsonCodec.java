package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.UUID;

public class AnalysisMessageJsonCodec {

    public QueuedAnalysisMessage fromPgmqRecord(long messageId, int readCount, String payload) {
        JsonObject json = readObject(payload);
        UploadReceivedEvent event = new UploadReceivedEvent(
                json.getInt("schema_version"),
                json.getString("event_type"),
                uuid(json, "upload_id"),
                uuid(json, "analysis_job_id"),
                uuid(json, "tenant_id"),
                uuid(json, "repository_id"),
                json.getString("commit_sha"));
        return new QueuedAnalysisMessage(messageId, readCount, event);
    }

    public String toDeadLetterJson(QueuedAnalysisMessage message, String reason) {
        var event = message.event();
        var json = Json.createObjectBuilder()
                .add("reason", reason == null ? "unknown" : reason)
                .add("source_message_id", message.messageId())
                .add("source_read_count", message.readCount())
                .add("message", Json.createObjectBuilder()
                        .add("schema_version", event.schemaVersion())
                        .add("event_type", event.eventType())
                        .add("upload_id", event.uploadId().toString())
                        .add("analysis_job_id", event.analysisJobId().toString())
                        .add("tenant_id", event.tenantId().toString())
                        .add("repository_id", event.repositoryId().toString())
                        .add("commit_sha", event.commitSha()))
                .build();
        StringWriter writer = new StringWriter();
        try (var jsonWriter = Json.createWriter(writer)) {
            jsonWriter.writeObject(json);
        }
        return writer.toString();
    }

    private static JsonObject readObject(String payload) {
        try (var reader = Json.createReader(new StringReader(payload))) {
            return reader.readObject();
        }
    }

    private static UUID uuid(JsonObject json, String fieldName) {
        return UUID.fromString(json.getString(fieldName));
    }
}
