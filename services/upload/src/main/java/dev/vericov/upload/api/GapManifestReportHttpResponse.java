package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageGapManifest;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record GapManifestReportHttpResponse(
        @JsonbProperty("report_id") UUID reportId,
        @JsonbProperty("upload_id") UUID uploadId,
        @JsonbProperty("commit_sha") String commitSha,
        String branch,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber,
        @JsonbProperty("gate_status") String gateStatus,
        @JsonbProperty("config_sha256") String configSha256) {

    public static GapManifestReportHttpResponse from(CoverageGapManifest manifest) {
        var resolved = manifest.resolved();
        return new GapManifestReportHttpResponse(
                resolved.reportId(),
                resolved.uploadId(),
                resolved.commitSha(),
                resolved.branch(),
                manifest.pullRequestNumber(),
                manifest.gateStatus(),
                manifest.configSha256());
    }
}
