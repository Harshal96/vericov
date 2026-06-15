package dev.vericov.analysis.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.analysis.adapter.jdbc.AnalysisMessageJsonCodec;
import dev.vericov.analysis.domain.AnalysisFailureDecision;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AnalysisComponentsTest {
    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID UPLOAD_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void defaultQueueIsInMemoryAndNoOpsWithoutDatabase() {
        assumeNoAnalysisDatabase();
        AnalysisComponents components = new AnalysisComponents();
        var queue = components.analysisJobQueue(new AnalysisMessageJsonCodec());
        var message = new QueuedAnalysisMessage(42L, 1, uploadReceivedEvent());

        assertTrue(queue.read("coverage_analysis_jobs", 300, 10).isEmpty());
        assertDoesNotThrow(() -> queue.archive("coverage_analysis_jobs", message.messageId()));
        assertDoesNotThrow(() -> queue.reschedule("coverage_analysis_jobs", message.messageId(), 30));
        assertDoesNotThrow(() -> queue.moveToDeadLetter(
                "coverage_analysis_jobs",
                "coverage_analysis_jobs_dlq",
                message,
                "failed"));
    }

    @Test
    void defaultRepositoryRetriesFailuresUntilAttemptBudgetIsExhausted() {
        assumeNoAnalysisDatabase();
        AnalysisComponents components = new AnalysisComponents();
        var repository = components.analysisJobRepository();
        Instant now = Instant.parse("2026-05-22T10:15:30Z");
        AnalysisFailureDecision decision = AnalysisFailureDecision.retryLater();

        for (int attempt = 1; attempt <= 5; attempt++) {
            repository.startJob(JOB_ID, "worker-1", now.plusSeconds(attempt));
            decision = repository.recordFailure(JOB_ID, "worker-1", now.plusSeconds(attempt), "boom");
        }

        assertTrue(decision.shouldDeadLetter());
        assertDoesNotThrow(() -> repository.completeJob(JOB_ID, now.plusSeconds(10)));
    }

    @Test
    void defaultRepositoryContextIsLocalAndEmpty() {
        AnalysisComponents components = new AnalysisComponents();

        var context = components.repositoryContextRepository()
                .loadContext(TENANT_ID, REPOSITORY_ID, "abc123", "main", 12);

        assertNotNull(context.contextVersion());
        assertTrue(context.findings().isEmpty());
        assertTrue(context.debtItems().isEmpty());
        assertTrue(context.componentRollups().isEmpty());
    }

    @Test
    void defaultProcessorFailsClearlyWithoutDatabaseBackedArtifactInputs() {
        assumeNoAnalysisDatabase();
        AnalysisComponents components = new AnalysisComponents();

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> components.coverageAnalysisProcessor(components.repositoryContextRepository())
                        .process(uploadReceivedEvent()));

        assertTrue(exception.getMessage().contains(JOB_ID.toString()));
    }

    @Test
    void analysisWorkerAndHandlerAreConstructedFromDefaultPorts() {
        assumeNoAnalysisDatabase();
        AnalysisComponents components = new AnalysisComponents();
        var queue = components.analysisJobQueue(components.analysisMessageJsonCodec());
        var repository = components.analysisJobRepository();
        var handler = components.analysisMessageHandler(
                queue,
                repository,
                event -> {
                    throw new UnsupportedOperationException("processor intentionally disabled");
                });

        assertNotNull(components.analysisWorker(queue, handler));
        assertFalse(components.analysisMessageJsonCodec()
                .toDeadLetterJson(new QueuedAnalysisMessage(7L, 2, uploadReceivedEvent()), "failed")
                .isBlank());
    }

    private static UploadReceivedEvent uploadReceivedEvent() {
        return new UploadReceivedEvent(
                1,
                "upload.received",
                UPLOAD_ID,
                JOB_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123");
    }

    private static void assumeNoAnalysisDatabase() {
        Assumptions.assumeTrue(env("VERICOV_ANALYSIS_DB_URL").isBlank());
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
