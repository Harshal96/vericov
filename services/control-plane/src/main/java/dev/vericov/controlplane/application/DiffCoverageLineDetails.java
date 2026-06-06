package dev.vericov.controlplane.application;

public record DiffCoverageLineDetails(
        String filePath,
        String oldFilePath,
        Integer baseLineNumber,
        Integer headLineNumber,
        String changeType,
        boolean executable,
        Long baseHits,
        Long headHits,
        boolean newlyMissed,
        boolean lostCoverage) {
}
