package dev.vericov.upload.application;

import java.util.List;

public record PatchCoverageDetails(
        String status,
        String baseSha,
        String headSha,
        long lineCovered,
        long lineTotal,
        long newlyMissedLineCount,
        long lostCoverageLineCount,
        List<PatchCoverageFileDetails> files) {

    public PatchCoverageDetails {
        files = List.copyOf(files == null ? List.of() : files);
    }
}
