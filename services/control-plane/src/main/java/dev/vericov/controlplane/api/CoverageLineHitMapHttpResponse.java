package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CoverageLineHitMapDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;
import java.util.UUID;

public record CoverageLineHitMapHttpResponse(
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("coverage_report_id")
        UUID coverageReportId,
        @JsonbProperty("commit_sha")
        String commitSha,
        Map<String, Map<Integer, Long>> files) {

    public static CoverageLineHitMapHttpResponse from(CoverageLineHitMapDetails details) {
        return new CoverageLineHitMapHttpResponse(
                details.repositoryId(),
                details.coverageReportId(),
                details.commitSha(),
                details.files());
    }
}
