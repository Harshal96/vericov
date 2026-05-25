package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.AnalysisJobQueue;
import java.util.Objects;

public class AnalysisWorker {
    private final AnalysisJobQueue queue;
    private final AnalysisMessageHandler handler;
    private final String queueName;
    private final int visibilityTimeoutSeconds;
    private final int batchSize;

    public AnalysisWorker(
            AnalysisJobQueue queue,
            AnalysisMessageHandler handler,
            String queueName,
            int visibilityTimeoutSeconds,
            int batchSize) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.queueName = Objects.requireNonNull(queueName, "queueName");
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.batchSize = batchSize;
    }

    public int pollOnce() {
        var messages = queue.read(queueName, visibilityTimeoutSeconds, batchSize);
        messages.forEach(handler::handle);
        return messages.size();
    }
}
