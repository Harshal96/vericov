package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.AnalysisJobQueue;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisWorkerTest {

    @Test
    void pollsQueueAndHandlesEachMessageInBatch() {
        FakeQueue queue = new FakeQueue();
        FakeHandler handler = new FakeHandler();
        AnalysisWorker worker = new AnalysisWorker(
                queue,
                handler,
                "coverage_analysis_jobs",
                120,
                10);
        queue.messages = List.of(message(1), message(2));

        int handled = worker.pollOnce();

        assertEquals(2, handled);
        assertEquals("coverage_analysis_jobs", queue.queueName);
        assertEquals(120, queue.visibilityTimeoutSeconds);
        assertEquals(10, queue.batchSize);
        assertEquals(List.of(1L, 2L), handler.handledMessageIds);
    }

    private static QueuedAnalysisMessage message(long messageId) {
        return new QueuedAnalysisMessage(
                messageId,
                1,
                new UploadReceivedEvent(
                        1,
                        "upload.received",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "abc123"));
    }

    private static final class FakeQueue implements AnalysisJobQueue {
        private List<QueuedAnalysisMessage> messages = List.of();
        private String queueName;
        private int visibilityTimeoutSeconds;
        private int batchSize;

        @Override
        public List<QueuedAnalysisMessage> read(String queueName, int visibilityTimeoutSeconds, int batchSize) {
            this.queueName = queueName;
            this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
            this.batchSize = batchSize;
            return messages;
        }

        @Override
        public void archive(String queueName, long messageId) {
        }

        @Override
        public void reschedule(String queueName, long messageId, int delaySeconds) {
        }

        @Override
        public void moveToDeadLetter(String sourceQueueName, String deadLetterQueueName, QueuedAnalysisMessage message, String reason) {
        }
    }

    private static final class FakeHandler implements AnalysisMessageHandler {
        private final List<Long> handledMessageIds = new ArrayList<>();

        @Override
        public void handle(QueuedAnalysisMessage message) {
            handledMessageIds.add(message.messageId());
        }
    }
}
