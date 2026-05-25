package dev.vericov.upload.application;

import java.util.UUID;

public record AnalysisJob(
        UUID jobId,
        UUID uploadId,
        UUID repositoryId,
        String commitSha) {
}
