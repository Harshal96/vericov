package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.AnalysisJobQueue;
import dev.vericov.analysis.application.port.AnalysisJobRepository;
import dev.vericov.analysis.application.port.CoverageAnalysisProcessor;
import dev.vericov.analysis.domain.AnalysisFailureDecision;
import dev.vericov.analysis.domain.NonRetryableAnalysisException;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import java.time.Clock;
import java.util.Objects;

public class UploadAnalysisEventHandler implements AnalysisMessageHandler {
    public static final String DEFAULT_QUEUE_NAME = "coverage_analysis_jobs";
    public static final String DEFAULT_DEAD_LETTER_QUEUE_NAME = "coverage_analysis_dead_letters";
    private static final int RETRY_DELAY_SECONDS = 60;

    private final AnalysisJobQueue queue;
    private final AnalysisJobRepository jobs;
    private final CoverageAnalysisProcessor processor;
    private final String workerId;
    private final Clock clock;
    private final String queueName;
    private final String deadLetterQueueName;

    public UploadAnalysisEventHandler(
            AnalysisJobQueue queue,
            AnalysisJobRepository jobs,
            CoverageAnalysisProcessor processor,
            String workerId,
            Clock clock) {
        this(
                queue,
                jobs,
                processor,
                workerId,
                clock,
                DEFAULT_QUEUE_NAME,
                DEFAULT_DEAD_LETTER_QUEUE_NAME);
    }

    public UploadAnalysisEventHandler(
            AnalysisJobQueue queue,
            AnalysisJobRepository jobs,
            CoverageAnalysisProcessor processor,
            String workerId,
            Clock clock,
            String queueName,
            String deadLetterQueueName) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.queueName = Objects.requireNonNull(queueName, "queueName");
        this.deadLetterQueueName = Objects.requireNonNull(deadLetterQueueName, "deadLetterQueueName");
    }

    @Override
    public void handle(QueuedAnalysisMessage message) {
        UploadReceivedEvent event = message.event();
        if (!event.isSupportedUploadReceived()) {
            deadLetterAndArchive(message, "Unsupported event type or schema version");
            return;
        }

        var startResult = jobs.startJob(event.analysisJobId(), workerId, clock.instant());
        if (!startResult.shouldProcess()) {
            if (startResult.shouldRetryClaimLater()) {
                queue.reschedule(queueName, message.messageId(), RETRY_DELAY_SECONDS);
                return;
            }
            queue.archive(queueName, message.messageId());
            return;
        }

        try {
            processor.process(event);
            jobs.completeJob(event.analysisJobId(), clock.instant());
            queue.archive(queueName, message.messageId());
        } catch (NonRetryableAnalysisException exception) {
            jobs.recordTerminalFailure(
                    event.analysisJobId(),
                    workerId,
                    clock.instant(),
                    exception.getMessage());
            deadLetterAndArchive(message, exception.getMessage());
        } catch (RuntimeException exception) {
            handleProcessingFailure(message, event, exception);
        }
    }

    private void handleProcessingFailure(
            QueuedAnalysisMessage message,
            UploadReceivedEvent event,
            RuntimeException exception) {
        AnalysisFailureDecision decision = jobs.recordFailure(
                event.analysisJobId(),
                workerId,
                clock.instant(),
                exception.getMessage());
        if (decision.shouldDeadLetter()) {
            deadLetterAndArchive(message, exception.getMessage());
            return;
        }
        queue.reschedule(queueName, message.messageId(), RETRY_DELAY_SECONDS);
    }

    private void deadLetterAndArchive(QueuedAnalysisMessage message, String reason) {
        queue.moveToDeadLetter(queueName, deadLetterQueueName, message, reason);
        queue.archive(queueName, message.messageId());
    }
}
