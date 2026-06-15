package dev.vericov.upload.application;

import dev.vericov.upload.domain.UploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record QueuedUpload(
        UUID uploadId,
        UUID tenantId,
        UUID repositoryId,
        Optional<UUID> apiKeyId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String ciProvider,
        String ciBuildId,
        String ciBuildUrl,
        List<String> flags,
        List<String> ignore,
        Optional<String> component,
        Optional<String> packageName,
        UploadStatus status,
        String idempotencyKey,
        Instant acceptedAt,
        Optional<UUID> analysisJobId) {

    public QueuedUpload {
        apiKeyId = apiKeyId == null ? Optional.empty() : apiKeyId;
        flags = List.copyOf(flags == null ? List.of() : flags);
        ignore = List.copyOf(ignore == null ? List.of() : ignore);
        component = component == null ? Optional.empty() : component;
        packageName = packageName == null ? Optional.empty() : packageName;
        analysisJobId = analysisJobId == null ? Optional.empty() : analysisJobId;
    }

    public QueuedUpload(
            UUID uploadId,
            UUID tenantId,
            UUID repositoryId,
            Optional<UUID> apiKeyId,
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            String ciProvider,
            String ciBuildId,
            String ciBuildUrl,
            List<String> flags,
            Optional<String> component,
            Optional<String> packageName,
            UploadStatus status,
            String idempotencyKey,
            Instant acceptedAt,
            Optional<UUID> analysisJobId) {
        this(
                uploadId,
                tenantId,
                repositoryId,
                apiKeyId,
                commitSha,
                branch,
                pullRequestNumber,
                ciProvider,
                ciBuildId,
                ciBuildUrl,
                flags,
                List.of(),
                component,
                packageName,
                status,
                idempotencyKey,
                acceptedAt,
                analysisJobId);
    }

    public QueuedUpload withAnalysisJobId(UUID analysisJobId) {
        return new QueuedUpload(
                uploadId,
                tenantId,
                repositoryId,
                apiKeyId,
                commitSha,
                branch,
                pullRequestNumber,
                ciProvider,
                ciBuildId,
                ciBuildUrl,
                flags,
                ignore,
                component,
                packageName,
                status,
                idempotencyKey,
                acceptedAt,
                Optional.of(analysisJobId));
    }
}
