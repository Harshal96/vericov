package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardTestRun;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record DashboardTestRunHttpResponse(
        UUID id,
        @JsonbProperty("suite_name") String suiteName,
        String status,
        @JsonbProperty("total_count") int totalCount,
        @JsonbProperty("passed_count") int passedCount,
        @JsonbProperty("failed_count") int failedCount,
        @JsonbProperty("error_count") int errorCount,
        @JsonbProperty("skipped_count") int skippedCount,
        @JsonbProperty("duration_ms") long durationMs,
        @JsonbProperty("commit_sha") String commitSha,
        String branch,
        @JsonbProperty("created_at") Instant createdAt) {
    public static DashboardTestRunHttpResponse from(DashboardTestRun run) {
        return new DashboardTestRunHttpResponse(
                run.id(),
                run.suiteName(),
                run.status(),
                run.totalCount(),
                run.passedCount(),
                run.failedCount(),
                run.errorCount(),
                run.skippedCount(),
                run.durationMs(),
                run.commitSha(),
                run.branch(),
                run.createdAt());
    }
}
