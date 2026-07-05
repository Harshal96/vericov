package dev.vericov.upload.application;

public record PatchCoverageFileDetails(
        String filePath,
        String oldFilePath,
        String changeStatus,
        long lineCovered,
        long lineTotal,
        long newlyMissedLineCount,
        long lostCoverageLineCount) {
}
