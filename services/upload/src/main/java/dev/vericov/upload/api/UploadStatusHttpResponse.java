package dev.vericov.upload.api;

import dev.vericov.upload.application.UploadDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UploadStatusHttpResponse(
        UUID id,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("commit_sha")
        String commitSha,
        String status,
        @JsonbProperty("analysis_job_id")
        UUID analysisJobId,
        List<UploadArtifactHttpResponse> artifacts,
        @JsonbProperty("created_at")
        Instant createdAt) {

    public UploadStatusHttpResponse {
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
    }

    public static UploadStatusHttpResponse from(UploadDetails upload) {
        return new UploadStatusHttpResponse(
                upload.uploadId(),
                upload.repositoryId(),
                upload.commitSha(),
                upload.status().wireValue(),
                upload.analysisJobId(),
                upload.artifacts().stream().map(UploadArtifactHttpResponse::from).toList(),
                upload.createdAt());
    }
}
