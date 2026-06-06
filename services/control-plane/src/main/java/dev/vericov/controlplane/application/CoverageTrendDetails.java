package dev.vericov.controlplane.application;

import java.util.List;
import java.util.UUID;

public record CoverageTrendDetails(
        UUID organizationId,
        UUID repositoryId,
        String branch,
        String metric,
        List<CoverageTrendPointDetails> points) {

    public CoverageTrendDetails {
        points = List.copyOf(points == null ? List.of() : points);
    }
}
