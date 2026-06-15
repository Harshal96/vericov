package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.AnalysisJobQueue;
import dev.vericov.analysis.application.port.AnalysisJobRepository;
import dev.vericov.analysis.application.port.CoverageAnalysisProcessor;
import dev.vericov.analysis.domain.AnalysisFailureDecision;
import dev.vericov.analysis.domain.AnalysisJobStartResult;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import dev.vericov.analysis.domain.NonRetryableAnalysisException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadAnalysisEventHandlerTest {
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID JOB_ID = UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1");

    @Test
    void processesUploadReceivedMessageAndArchivesIt() {
        TestFixture fixture = new TestFixture();

        fixture.handler.handle(message());

        assertEquals(List.of(JOB_ID), fixture.repository.startedJobs);
        assertEquals(List.of(event()), fixture.processor.processedEvents);
        assertEquals(List.of(JOB_ID), fixture.repository.completedJobs);
        assertEquals(List.of(101L), fixture.queue.archivedMessageIds);
        assertTrue(fixture.queue.deadLetters.isEmpty());
        assertTrue(fixture.queue.rescheduledMessageIds.isEmpty());
    }

    @Test
    void archivesMessageWithoutProcessingWhenJobAlreadyCompleted() {
        TestFixture fixture = new TestFixture();
        fixture.repository.startResult = AnalysisJobStartResult.alreadyFinished();

        fixture.handler.handle(message());

        assertEquals(List.of(JOB_ID), fixture.repository.startedJobs);
        assertTrue(fixture.processor.processedEvents.isEmpty());
        assertTrue(fixture.repository.completedJobs.isEmpty());
        assertEquals(List.of(101L), fixture.queue.archivedMessageIds);
    }

    @Test
    void reschedulesMessageWithoutProcessingWhenJobIsAlreadyLocked() {
        TestFixture fixture = new TestFixture();
        fixture.repository.startResult = AnalysisJobStartResult.busy();

        fixture.handler.handle(message());

        assertEquals(List.of(JOB_ID), fixture.repository.startedJobs);
        assertTrue(fixture.processor.processedEvents.isEmpty());
        assertTrue(fixture.repository.completedJobs.isEmpty());
        assertEquals(List.of(101L), fixture.queue.rescheduledMessageIds);
        assertTrue(fixture.queue.archivedMessageIds.isEmpty());
    }

    @Test
    void reschedulesMessageWhenProcessingFailsAndAttemptsRemain() {
        TestFixture fixture = new TestFixture();
        fixture.processor.failure = new IllegalStateException("parser is unavailable");
        fixture.repository.failureDecision = AnalysisFailureDecision.retryLater();

        fixture.handler.handle(message());

        assertEquals(List.of(JOB_ID), fixture.repository.startedJobs);
        assertTrue(fixture.repository.completedJobs.isEmpty());
        assertEquals(List.of(JOB_ID), fixture.repository.failedJobs);
        assertEquals(List.of(101L), fixture.queue.rescheduledMessageIds);
        assertTrue(fixture.queue.archivedMessageIds.isEmpty());
        assertTrue(fixture.queue.deadLetters.isEmpty());
    }

    @Test
    void deadLettersMessageWhenProcessingFailsAfterMaxAttempts() {
        TestFixture fixture = new TestFixture();
        fixture.processor.failure = new IllegalStateException("invalid coverage artifact");
        fixture.repository.failureDecision = AnalysisFailureDecision.deadLetter();

        fixture.handler.handle(message());

        assertEquals(List.of(JOB_ID), fixture.repository.startedJobs);
        assertEquals(List.of(JOB_ID), fixture.repository.failedJobs);
        assertEquals(List.of(101L), fixture.queue.deadLetters);
        assertEquals(List.of(101L), fixture.queue.archivedMessageIds);
        assertTrue(fixture.queue.rescheduledMessageIds.isEmpty());
    }

    @Test
    void immediatelyFailsAndDeadLettersNonRetryableInput() {
        TestFixture fixture = new TestFixture();
        fixture.processor.failure = new NonRetryableAnalysisException("invalid persisted ignore rules");

        fixture.handler.handle(message());

        assertEquals(List.of(JOB_ID), fixture.repository.terminallyFailedJobs);
        assertTrue(fixture.repository.failedJobs.isEmpty());
        assertEquals(List.of(101L), fixture.queue.deadLetters);
        assertEquals(List.of(101L), fixture.queue.archivedMessageIds);
        assertTrue(fixture.queue.rescheduledMessageIds.isEmpty());
    }

    @Test
    void deadLettersUnsupportedEvents() {
        TestFixture fixture = new TestFixture();
        UploadReceivedEvent unsupported = new UploadReceivedEvent(
                1,
                "coverage.recalculation.requested",
                UPLOAD_ID,
                JOB_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123");

        fixture.handler.handle(new QueuedAnalysisMessage(101L, 1, unsupported));

        assertTrue(fixture.repository.startedJobs.isEmpty());
        assertTrue(fixture.processor.processedEvents.isEmpty());
        assertEquals(List.of(101L), fixture.queue.deadLetters);
        assertEquals(List.of(101L), fixture.queue.archivedMessageIds);
    }

    private static QueuedAnalysisMessage message() {
        return new QueuedAnalysisMessage(101L, 1, event());
    }

    private static UploadReceivedEvent event() {
        return new UploadReceivedEvent(
                1,
                "upload.received",
                UPLOAD_ID,
                JOB_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123");
    }

    private static final class TestFixture {
        private final FakeQueue queue = new FakeQueue();
        private final FakeRepository repository = new FakeRepository();
        private final FakeProcessor processor = new FakeProcessor();
        private final UploadAnalysisEventHandler handler = new UploadAnalysisEventHandler(
                queue,
                repository,
                processor,
                "worker-1",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FakeQueue implements AnalysisJobQueue {
        private final List<Long> archivedMessageIds = new ArrayList<>();
        private final List<Long> rescheduledMessageIds = new ArrayList<>();
        private final List<Long> deadLetters = new ArrayList<>();

        @Override
        public List<QueuedAnalysisMessage> read(String queueName, int visibilityTimeoutSeconds, int batchSize) {
            return List.of();
        }

        @Override
        public void archive(String queueName, long messageId) {
            archivedMessageIds.add(messageId);
        }

        @Override
        public void reschedule(String queueName, long messageId, int delaySeconds) {
            rescheduledMessageIds.add(messageId);
        }

        @Override
        public void moveToDeadLetter(String sourceQueueName, String deadLetterQueueName, QueuedAnalysisMessage message, String reason) {
            deadLetters.add(message.messageId());
        }
    }

    private static final class FakeRepository implements AnalysisJobRepository {
        private final List<UUID> startedJobs = new ArrayList<>();
        private final List<UUID> completedJobs = new ArrayList<>();
        private final List<UUID> failedJobs = new ArrayList<>();
        private final List<UUID> terminallyFailedJobs = new ArrayList<>();
        private AnalysisJobStartResult startResult = AnalysisJobStartResult.started();
        private AnalysisFailureDecision failureDecision = AnalysisFailureDecision.retryLater();

        @Override
        public AnalysisJobStartResult startJob(UUID jobId, String workerId, Instant startedAt) {
            startedJobs.add(jobId);
            return startResult;
        }

        @Override
        public void completeJob(UUID jobId, Instant finishedAt) {
            completedJobs.add(jobId);
        }

        @Override
        public AnalysisFailureDecision recordFailure(UUID jobId, String workerId, Instant failedAt, String errorMessage) {
            failedJobs.add(jobId);
            return failureDecision;
        }

        @Override
        public void recordTerminalFailure(UUID jobId, String workerId, Instant failedAt, String errorMessage) {
            terminallyFailedJobs.add(jobId);
        }
    }

    private static final class FakeProcessor implements CoverageAnalysisProcessor {
        private final List<UploadReceivedEvent> processedEvents = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void process(UploadReceivedEvent event) {
            processedEvents.add(event);
            if (failure != null) {
                throw failure;
            }
        }
    }
}
