package dev.vericov.analysis.application.port;

import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import java.util.List;

public interface AnalysisJobQueue {
    List<QueuedAnalysisMessage> read(String queueName, int visibilityTimeoutSeconds, int batchSize);

    void archive(String queueName, long messageId);

    void reschedule(String queueName, long messageId, int delaySeconds);

    void moveToDeadLetter(
            String sourceQueueName,
            String deadLetterQueueName,
            QueuedAnalysisMessage message,
            String reason);
}
