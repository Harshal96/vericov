package dev.vericov.upload.api;

import dev.vericov.upload.application.ArtifactDetails;
import jakarta.json.bind.annotation.JsonbProperty;

public record UploadArtifactHttpResponse(
        String name,
        String kind,
        String format,
        String status,
        @JsonbProperty("size_bytes")
        long sizeBytes) {

    public static UploadArtifactHttpResponse from(ArtifactDetails artifact) {
        return new UploadArtifactHttpResponse(
                artifact.name(),
                artifact.kind().wireValue(),
                artifact.format(),
                artifact.status(),
                artifact.sizeBytes());
    }
}
