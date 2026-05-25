package dev.vericov.analysis.domain;

public record QueuedAnalysisMessage(
        long messageId,
        int readCount,
        UploadReceivedEvent event) {
}
