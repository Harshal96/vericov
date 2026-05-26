package dev.vericov.agent.application;

import java.util.List;
import java.util.UUID;

public record AgentTaskSource(
        String type,
        UUID coverageReportId,
        List<UUID> coverageGapFindingIds,
        Integer pullRequestNumber,
        String commitSha,
        String baseSha,
        String headSha) {
    public AgentTaskSource {
        type = AgentValues.requireCanonical(type, "source.type is required");
        if (!"coverage_gap".equals(type)) {
            throw new AgentRunnerException("validation_error", "source.type is unsupported");
        }
        coverageReportId = AgentValues.requireUuid(coverageReportId, "source.coverage_report_id");
        coverageGapFindingIds = AgentValues.requireNonEmptyList(
                coverageGapFindingIds,
                "source.coverage_gap_finding_ids");
        if (pullRequestNumber != null && pullRequestNumber <= 0) {
            throw new AgentRunnerException("validation_error", "source.pull_request_number must be positive");
        }
        commitSha = AgentValues.requireTrimmed(commitSha, "source.commit_sha is required");
        baseSha = AgentValues.optionalTrimmed(baseSha);
        headSha = AgentValues.optionalTrimmed(headSha);
    }

    public static AgentTaskSource coverageGap(
            UUID coverageReportId,
            List<UUID> coverageGapFindingIds,
            Integer pullRequestNumber,
            String commitSha,
            String baseSha,
            String headSha) {
        return new AgentTaskSource(
                "coverage_gap",
                coverageReportId,
                coverageGapFindingIds,
                pullRequestNumber,
                commitSha,
                baseSha,
                headSha);
    }
}
