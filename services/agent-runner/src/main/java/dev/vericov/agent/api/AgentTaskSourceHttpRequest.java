package dev.vericov.agent.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record AgentTaskSourceHttpRequest(
        String type,
        @JsonbProperty("coverage_report_id") String coverageReportId,
        @JsonbProperty("coverage_gap_finding_ids") List<String> coverageGapFindingIds,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber,
        @JsonbProperty("commit_sha") String commitSha,
        @JsonbProperty("base_sha") String baseSha,
        @JsonbProperty("head_sha") String headSha) {
}
