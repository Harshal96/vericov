package dev.vericov.controlplane.application;

import java.util.Map;
import java.util.UUID;

public record CoverageLineHitMapDetails(
        UUID repositoryId,
        UUID coverageReportId,
        String commitSha,
        Map<String, Map<Integer, Long>> files) {

    public CoverageLineHitMapDetails {
        files = Map.copyOf(files == null ? Map.of() : files);
    }
}
