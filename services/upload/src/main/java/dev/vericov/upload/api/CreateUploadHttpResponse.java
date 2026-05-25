package dev.vericov.upload.api;

import dev.vericov.upload.application.UploadAccepted;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record CreateUploadHttpResponse(
        @JsonbProperty("upload_id")
        UUID uploadId,
        String status,
        @JsonbProperty("poll_url")
        String pollUrl,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("commit_sha")
        String commitSha,
        @JsonbProperty("analysis_job_id")
        UUID analysisJobId) {

    public static CreateUploadHttpResponse from(UploadAccepted accepted) {
        return new CreateUploadHttpResponse(
                accepted.uploadId(),
                accepted.status().wireValue(),
                accepted.pollUrl(),
                accepted.repositoryId(),
                accepted.commitSha(),
                accepted.analysisJobId());
    }
}
