package dev.vericov.organization.api;

import dev.vericov.organization.application.TestRunDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record TestRunHttpResponse(
        UUID id,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("upload_id")
        UUID uploadId,
        @JsonbProperty("upload_artifact_id")
        UUID uploadArtifactId,
        @JsonbProperty("commit_sha")
        String commitSha,
        String branch,
        @JsonbProperty("pull_request_number")
        Integer pullRequestNumber,
        @JsonbProperty("suite_name")
        String suiteName,
        @JsonbProperty("suite_index")
        int suiteIndex,
        String status,
        @JsonbProperty("total_count")
        int totalCount,
        @JsonbProperty("passed_count")
        int passedCount,
        @JsonbProperty("failed_count")
        int failedCount,
        @JsonbProperty("error_count")
        int errorCount,
        @JsonbProperty("skipped_count")
        int skippedCount,
        @JsonbProperty("duration_ms")
        long durationMs,
        @JsonbProperty("created_at")
        Instant createdAt) {

    public static TestRunHttpResponse from(TestRunDetails details) {
        return new TestRunHttpResponse(
                details.id(),
                details.repositoryId(),
                details.uploadId(),
                details.uploadArtifactId(),
                details.commitSha(),
                details.branch(),
                details.pullRequestNumber(),
                details.suiteName(),
                details.suiteIndex(),
                details.status(),
                details.totalCount(),
                details.passedCount(),
                details.failedCount(),
                details.errorCount(),
                details.skippedCount(),
                details.durationMs(),
                details.createdAt());
    }
}
