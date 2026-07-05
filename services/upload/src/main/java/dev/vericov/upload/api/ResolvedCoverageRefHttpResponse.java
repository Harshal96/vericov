package dev.vericov.upload.api;

import dev.vericov.upload.application.ResolvedCoverageRef;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record ResolvedCoverageRefHttpResponse(
        @JsonbProperty("report_id") UUID reportId,
        @JsonbProperty("upload_id") UUID uploadId,
        @JsonbProperty("commit_sha") String commitSha,
        String branch,
        @JsonbProperty("created_at") Instant createdAt) {

    public static ResolvedCoverageRefHttpResponse from(ResolvedCoverageRef resolved) {
        return new ResolvedCoverageRefHttpResponse(
                resolved.reportId(),
                resolved.uploadId(),
                resolved.commitSha(),
                resolved.branch(),
                resolved.createdAt());
    }
}
