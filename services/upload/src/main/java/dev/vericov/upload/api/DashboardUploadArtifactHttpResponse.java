package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardUploadArtifact;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;

public record DashboardUploadArtifactHttpResponse(
        String name,
        String kind,
        String format,
        @JsonbProperty("size_bytes") long sizeBytes,
        @JsonbProperty("created_at") Instant createdAt) {
    public static DashboardUploadArtifactHttpResponse from(DashboardUploadArtifact artifact) {
        return new DashboardUploadArtifactHttpResponse(
                artifact.name(), artifact.kind(), artifact.format(), artifact.sizeBytes(), artifact.createdAt());
    }
}
