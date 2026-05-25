package dev.vericov.analysis.domain;

import java.util.UUID;

public record UploadReceivedEvent(
        int schemaVersion,
        String eventType,
        UUID uploadId,
        UUID analysisJobId,
        UUID tenantId,
        UUID repositoryId,
        String commitSha) {

    public boolean isSupportedUploadReceived() {
        return schemaVersion == 1 && "upload.received".equals(eventType);
    }
}
