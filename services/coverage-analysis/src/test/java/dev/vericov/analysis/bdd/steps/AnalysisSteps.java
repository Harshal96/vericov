package dev.vericov.analysis.bdd.steps;

import dev.vericov.analysis.application.AnalysisMessageHandler;
import dev.vericov.analysis.application.AnalysisWorker;
import dev.vericov.analysis.application.DefaultCoverageAnalysisProcessor;
import dev.vericov.analysis.application.UploadAnalysisEventHandler;
import dev.vericov.analysis.application.port.AnalysisJobQueue;
import dev.vericov.analysis.application.port.AnalysisJobRepository;
import dev.vericov.analysis.application.port.ArtifactContentStore;
import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.application.port.NormalizedCoverageLocation;
import dev.vericov.analysis.application.port.NormalizedCoverageStore;
import dev.vericov.analysis.application.port.TestRunRepository;
import dev.vericov.analysis.coverage.CloverCoverageParser;
import dev.vericov.analysis.coverage.CoberturaCoverageParser;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageParserRegistry;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportSummary;
import dev.vericov.analysis.coverage.GcovCoverageParser;
import dev.vericov.analysis.coverage.GoCoverProfileParser;
import dev.vericov.analysis.coverage.JacocoCoverageParser;
import dev.vericov.analysis.coverage.LcovCoverageParser;
import dev.vericov.analysis.coverage.SecureXmlCoverageDocumentReader;
import dev.vericov.analysis.domain.AnalysisFailureDecision;
import dev.vericov.analysis.domain.AnalysisJobStartResult;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import dev.vericov.analysis.gates.GateConfiguration;
import dev.vericov.analysis.gates.GateEvaluation;
import dev.vericov.analysis.testresults.JUnitTestResultParser;
import dev.vericov.analysis.testresults.SecureXmlTestResultDocumentReader;
import dev.vericov.analysis.testresults.TestResultParserRegistry;
import dev.vericov.analysis.testresults.TestRun;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnalysisSteps {
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID JOB_ID = UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1");
    private static final UUID TEST_ARTIFACT_ID = UUID.fromString("52d0e554-4ce9-418c-96af-2c1c4cf17e3c");
    private static final long MESSAGE_ID = 101L;

    private CoverageAnalysisInput input;
    private QueuedAnalysisMessage message;
    private final FakeQueue queue = new FakeQueue();
    private final FakeJobRepository jobs = new FakeJobRepository();
    private final FakeContentStore contentStore = new FakeContentStore();
    private final FakeNormalizedCoverageStore normalizedCoverageStore = new FakeNormalizedCoverageStore();
    private final FakeReportRepository reports = new FakeReportRepository();
    private final FakeGateConfigurationRepository gates = new FakeGateConfigurationRepository();
    private final FakeTestRunRepository testRuns = new FakeTestRunRepository();

    @Given("an upload received message with LCOV coverage artifacts")
    public void uploadReceivedMessageWithLcovCoverageArtifacts() {
        input = new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(
                        new CoverageInputArtifact(
                                "unit.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/unit.lcov",
                                "sha-1"),
                        new CoverageInputArtifact(
                                "integration.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/integration.lcov",
                                "sha-2")));
        message = new QueuedAnalysisMessage(MESSAGE_ID, 1, supportedEvent());
        queue.messages = List.of(message);
    }

    @Given("object storage contains the LCOV shards")
    public void objectStorageContainsTheLcovShards() {
        contentStore.contentByLocation.put(
                "coverage-raw/tenant/upload/coverage/unit.lcov",
                """
                TN:
                SF:src/App.java
                DA:1,1
                DA:2,0
                BRDA:1,0,0,1
                end_of_record
                """.getBytes(StandardCharsets.UTF_8));
        contentStore.contentByLocation.put(
                "coverage-raw/tenant/upload/coverage/integration.lcov",
                """
                TN:
                SF:src/App.java
                DA:2,7
                DA:3,1
                BRDA:2,0,0,0
                end_of_record
                """.getBytes(StandardCharsets.UTF_8));
    }

    @Given("an active project coverage gate requiring {int} percent line coverage")
    public void activeProjectCoverageGateRequiringPercentLineCoverage(int threshold) {
        gates.gates = List.of(new GateConfiguration(
                UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
                TENANT_ID,
                REPOSITORY_ID,
                "line-minimum",
                "project_coverage",
                "line",
                new BigDecimal(threshold),
                null,
                true,
                Map.of(),
                "active"));
    }

    @Given("an upload received message with LCOV and JaCoCo coverage artifacts")
    public void uploadReceivedMessageWithLcovAndJacocoCoverageArtifacts() {
        input = new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(
                        new CoverageInputArtifact(
                                "unit.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/unit.lcov",
                                "sha-1"),
                        new CoverageInputArtifact(
                                "jacoco.xml",
                                "coverage",
                                "jacoco",
                                "coverage-raw",
                                "tenant/upload/coverage/jacoco.xml",
                                "sha-2")));
        message = new QueuedAnalysisMessage(MESSAGE_ID, 1, supportedEvent());
        queue.messages = List.of(message);
    }

    @Given("object storage contains the mixed coverage shards")
    public void objectStorageContainsTheMixedCoverageShards() {
        contentStore.contentByLocation.put(
                "coverage-raw/tenant/upload/coverage/unit.lcov",
                """
                TN:
                SF:src/App.java
                DA:1,1
                DA:2,0
                end_of_record
                """.getBytes(StandardCharsets.UTF_8));
        contentStore.contentByLocation.put(
                "coverage-raw/tenant/upload/coverage/jacoco.xml",
                """
                <report name="unit">
                  <package name="src">
                    <class name="src/App" sourcefilename="App.java">
                      <method name="run" desc="()V" line="2">
                        <counter type="METHOD" missed="0" covered="1" />
                      </method>
                    </class>
                    <sourcefile name="App.java">
                      <line nr="2" mi="0" ci="1" mb="0" cb="0" />
                      <line nr="3" mi="0" ci="1" mb="0" cb="0" />
                    </sourcefile>
                  </package>
                </report>
                """.getBytes(StandardCharsets.UTF_8));
    }

    @Given("an upload received message with JUnit test-result artifacts")
    public void uploadReceivedMessageWithJunitTestResultArtifacts() {
        input = new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(new CoverageInputArtifact(
                        TEST_ARTIFACT_ID,
                        "junit.xml",
                        "test_results",
                        "junit",
                        "test-results-raw",
                        "tenant/upload/test-results/junit.xml",
                        "sha-1")));
        message = new QueuedAnalysisMessage(MESSAGE_ID, 1, supportedEvent());
        queue.messages = List.of(message);
    }

    @Given("object storage contains the JUnit test results")
    public void objectStorageContainsTheJunitTestResults() {
        contentStore.contentByLocation.put(
                "test-results-raw/tenant/upload/test-results/junit.xml",
                """
                <testsuite name="unit" tests="3" failures="1" errors="0" skipped="0" time="0.42" />
                """.getBytes(StandardCharsets.UTF_8));
    }

    @Given("an upload received message without analyzable artifacts")
    public void uploadReceivedMessageWithoutAnalyzableArtifacts() {
        input = new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(new CoverageInputArtifact(
                        "metadata.json",
                        "metadata",
                        "json",
                        "metadata-raw",
                        "tenant/upload/metadata/metadata.json",
                        "sha-1")));
        message = new QueuedAnalysisMessage(MESSAGE_ID, 1, supportedEvent());
        queue.messages = List.of(message);
    }

    @Given("the analysis job is already locked")
    public void analysisJobIsAlreadyLocked() {
        jobs.startResult = AnalysisJobStartResult.busy();
    }

    @Given("the analysis job is already completed")
    public void analysisJobIsAlreadyCompleted() {
        jobs.startResult = AnalysisJobStartResult.alreadyFinished();
    }

    @Given("the analysis retry budget is exhausted")
    public void analysisRetryBudgetIsExhausted() {
        jobs.failureDecision = AnalysisFailureDecision.deadLetter();
    }

    @Given("an unsupported analysis message")
    public void unsupportedAnalysisMessage() {
        UploadReceivedEvent unsupported = new UploadReceivedEvent(
                1,
                "coverage.recalculation.requested",
                UPLOAD_ID,
                JOB_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123");
        message = new QueuedAnalysisMessage(MESSAGE_ID, 1, unsupported);
        queue.messages = List.of(message);
    }

    @When("the analysis worker polls once")
    public void analysisWorkerPollsOnce() {
        AnalysisWorker worker = new AnalysisWorker(
                queue,
                handler(),
                UploadAnalysisEventHandler.DEFAULT_QUEUE_NAME,
                120,
                10);

        int handled = worker.pollOnce();

        assertEquals(1, handled);
        assertEquals(UploadAnalysisEventHandler.DEFAULT_QUEUE_NAME, queue.readQueueName);
        assertEquals(120, queue.visibilityTimeoutSeconds);
        assertEquals(10, queue.batchSize);
    }

    @Then("the coverage report is persisted with {int} covered lines out of {int}")
    public void coverageReportIsPersistedWithCoveredLinesOutOfTotal(int covered, int total) {
        CoverageReport report = reports.savedReport;
        assertNotNull(report);
        assertEquals(UPLOAD_ID, report.uploadId());
        assertEquals(TENANT_ID, report.tenantId());
        assertEquals(REPOSITORY_ID, report.repositoryId());
        assertEquals("abc123", report.commitSha());
        assertEquals("main", report.branchName());
        assertEquals(42, report.pullRequestNumber());
        assertEquals(covered, report.line().covered());
        assertEquals(total, report.line().total());
        assertEquals(NOW, report.generatedAt());
    }

    @Then("the analysis job is completed")
    public void analysisJobIsCompleted() {
        assertEquals(List.of(JOB_ID), jobs.completedJobs);
        assertTrue(jobs.failedJobs.isEmpty());
    }

    @Then("the queue message is archived")
    public void queueMessageIsArchived() {
        assertEquals(List.of(MESSAGE_ID), queue.archivedMessageIds);
    }

    @Then("the analysis job failure is recorded")
    public void analysisJobFailureIsRecorded() {
        assertEquals(List.of(JOB_ID), jobs.failedJobs);
        assertTrue(jobs.completedJobs.isEmpty());
    }

    @Then("the queue message is rescheduled")
    public void queueMessageIsRescheduled() {
        assertEquals(List.of(MESSAGE_ID), queue.rescheduledMessageIds);
        assertTrue(queue.archivedMessageIds.isEmpty());
    }

    @Then("no coverage report is persisted")
    public void noCoverageReportIsPersisted() {
        assertNull(reports.savedReport);
    }

    @Then("the test run is persisted with {int} passed tests out of {int}")
    public void testRunIsPersistedWithPassedTestsOutOfTotal(int passed, int total) {
        assertEquals(1, testRuns.savedRuns.size());
        TestRun run = testRuns.savedRuns.getFirst();
        assertEquals(UPLOAD_ID, run.uploadId());
        assertEquals(TEST_ARTIFACT_ID, run.uploadArtifactId());
        assertEquals("unit", run.suiteName());
        assertEquals(total, run.totalCount());
        assertEquals(passed, run.passedCount());
        assertEquals("failed", run.status());
    }

    @Then("the coverage artifacts are not downloaded")
    public void coverageArtifactsAreNotDownloaded() {
        assertTrue(contentStore.readLocations.isEmpty());
    }

    @Then("a normalized coverage map is stored")
    public void normalizedCoverageMapIsStored() {
        CoverageReport report = reports.savedReport;
        assertNotNull(report);
        assertEquals(1, normalizedCoverageStore.storedReports.size());
        assertEquals("coverage-normalized", report.normalizedStorageBucket());
        assertEquals(
                TENANT_ID + "/" + UPLOAD_ID + "/coverage-normalized/coverage-map.json.gz",
                report.normalizedStoragePath());
    }

    @Then("the queue message is moved to the dead-letter queue")
    public void queueMessageIsMovedToTheDeadLetterQueue() {
        assertEquals(List.of(MESSAGE_ID), queue.deadLetterMessageIds);
        assertEquals(List.of("Unsupported event type or schema version"), queue.deadLetterReasons);
    }

    @Then("the queue message is moved to the dead-letter queue with a processing failure")
    public void queueMessageIsMovedToTheDeadLetterQueueWithAProcessingFailure() {
        assertEquals(List.of(MESSAGE_ID), queue.deadLetterMessageIds);
        assertEquals(1, queue.deadLetterReasons.size());
        assertFalse(queue.deadLetterReasons.getFirst().isBlank());
    }

    @Then("analysis processing is not started")
    public void analysisProcessingIsNotStarted() {
        assertTrue(jobs.startedJobs.isEmpty());
        assertNull(reports.savedReport);
        assertTrue(contentStore.readLocations.isEmpty());
    }

    @Then("a passed line coverage gate evaluation is persisted")
    public void passedLineCoverageGateEvaluationIsPersisted() {
        assertEquals(1, reports.savedEvaluations.size());
        GateEvaluation evaluation = reports.savedEvaluations.getFirst();
        assertEquals("line-minimum", evaluation.gateName());
        assertEquals("passed", evaluation.status());
        assertEquals("line", evaluation.metric());
    }

    private AnalysisMessageHandler handler() {
        return new UploadAnalysisEventHandler(
                queue,
                jobs,
                processor(),
                "worker-1",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DefaultCoverageAnalysisProcessor processor() {
        return new DefaultCoverageAnalysisProcessor(
                new FakeInputRepository(),
                contentStore,
                reports,
                gates,
                normalizedCoverageStore,
                parserRegistry(),
                testResultParserRegistry(),
                testRuns,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CoverageParserRegistry parserRegistry() {
        SecureXmlCoverageDocumentReader xmlReader = new SecureXmlCoverageDocumentReader();
        return new CoverageParserRegistry(List.of(
                new LcovCoverageParser(),
                new CoberturaCoverageParser(xmlReader),
                new JacocoCoverageParser(xmlReader),
                new CloverCoverageParser(xmlReader),
                new GoCoverProfileParser(),
                new GcovCoverageParser()));
    }

    private static TestResultParserRegistry testResultParserRegistry() {
        return new TestResultParserRegistry(List.of(new JUnitTestResultParser(new SecureXmlTestResultDocumentReader())));
    }

    private static UploadReceivedEvent supportedEvent() {
        return new UploadReceivedEvent(
                1,
                "upload.received",
                UPLOAD_ID,
                JOB_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123");
    }

    private final class FakeInputRepository implements CoverageAnalysisInputRepository {
        @Override
        public CoverageAnalysisInput load(UUID uploadId) {
            assertEquals(UPLOAD_ID, uploadId);
            return input;
        }
    }

    private static final class FakeContentStore implements ArtifactContentStore {
        private final Map<String, byte[]> contentByLocation = new HashMap<>();
        private final List<String> readLocations = new ArrayList<>();

        @Override
        public byte[] read(String bucket, String storagePath) {
            String location = bucket + "/" + storagePath;
            readLocations.add(location);
            return contentByLocation.get(location);
        }
    }

    private static final class FakeReportRepository implements CoverageReportRepository {
        private CoverageReport savedReport;
        private List<GateEvaluation> savedEvaluations = List.of();

        @Override
        public void save(CoverageReport report) {
            savedReport = report;
        }

        @Override
        public void save(CoverageReport report, List<GateEvaluation> evaluations) {
            savedReport = report;
            savedEvaluations = List.copyOf(evaluations);
        }

        @Override
        public Optional<CoverageReportSummary> findLatestByCommit(UUID repositoryId, String commitSha) {
            return Optional.empty();
        }

        @Override
        public List<CoverageLineHit> findLineHits(UUID coverageReportId) {
            return List.of();
        }

        @Override
        public List<CoverageLineHit> findLineHits(UUID coverageReportId, String filePath) {
            return List.of();
        }
    }

    private static final class FakeGateConfigurationRepository implements GateConfigurationRepository {
        private List<GateConfiguration> gates = List.of();

        @Override
        public List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId) {
            assertEquals(TENANT_ID, tenantId);
            assertEquals(REPOSITORY_ID, repositoryId);
            return gates;
        }
    }

    private static final class FakeNormalizedCoverageStore implements NormalizedCoverageStore {
        private final List<CoverageReport> storedReports = new ArrayList<>();

        @Override
        public NormalizedCoverageLocation store(CoverageReport report) {
            storedReports.add(report);
            return new NormalizedCoverageLocation(
                    "coverage-normalized",
                    report.tenantId() + "/" + report.uploadId() + "/coverage-normalized/coverage-map.json.gz");
        }
    }

    private static final class FakeTestRunRepository implements TestRunRepository {
        private List<TestRun> savedRuns = List.of();

        @Override
        public void save(CoverageAnalysisInput input, List<TestRun> runs, Instant completedAt) {
            assertEquals(UPLOAD_ID, input.uploadId());
            assertEquals(NOW, completedAt);
            savedRuns = List.copyOf(runs);
        }
    }

    private static final class FakeQueue implements AnalysisJobQueue {
        private List<QueuedAnalysisMessage> messages = List.of();
        private final List<Long> archivedMessageIds = new ArrayList<>();
        private final List<Long> rescheduledMessageIds = new ArrayList<>();
        private final List<Long> deadLetterMessageIds = new ArrayList<>();
        private final List<String> deadLetterReasons = new ArrayList<>();
        private String readQueueName;
        private int visibilityTimeoutSeconds;
        private int batchSize;

        @Override
        public List<QueuedAnalysisMessage> read(String queueName, int visibilityTimeoutSeconds, int batchSize) {
            readQueueName = queueName;
            this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
            this.batchSize = batchSize;
            return messages;
        }

        @Override
        public void archive(String queueName, long messageId) {
            archivedMessageIds.add(messageId);
        }

        @Override
        public void reschedule(String queueName, long messageId, int delaySeconds) {
            assertEquals(60, delaySeconds);
            rescheduledMessageIds.add(messageId);
        }

        @Override
        public void moveToDeadLetter(
                String sourceQueueName,
                String deadLetterQueueName,
                QueuedAnalysisMessage message,
                String reason) {
            assertEquals(UploadAnalysisEventHandler.DEFAULT_QUEUE_NAME, sourceQueueName);
            assertEquals(UploadAnalysisEventHandler.DEFAULT_DEAD_LETTER_QUEUE_NAME, deadLetterQueueName);
            deadLetterMessageIds.add(message.messageId());
            deadLetterReasons.add(reason);
        }
    }

    private static final class FakeJobRepository implements AnalysisJobRepository {
        private final List<UUID> startedJobs = new ArrayList<>();
        private final List<UUID> completedJobs = new ArrayList<>();
        private final List<UUID> failedJobs = new ArrayList<>();
        private AnalysisJobStartResult startResult = AnalysisJobStartResult.started();
        private AnalysisFailureDecision failureDecision = AnalysisFailureDecision.retryLater();

        @Override
        public AnalysisJobStartResult startJob(UUID jobId, String workerId, Instant startedAt) {
            assertEquals("worker-1", workerId);
            assertEquals(NOW, startedAt);
            startedJobs.add(jobId);
            return startResult;
        }

        @Override
        public void completeJob(UUID jobId, Instant finishedAt) {
            assertEquals(NOW, finishedAt);
            completedJobs.add(jobId);
        }

        @Override
        public AnalysisFailureDecision recordFailure(
                UUID jobId,
                String workerId,
                Instant failedAt,
                String errorMessage) {
            assertEquals("worker-1", workerId);
            assertEquals(NOW, failedAt);
            assertFalseBlank(errorMessage);
            failedJobs.add(jobId);
            return failureDecision;
        }

        @Override
        public void recordTerminalFailure(
                UUID jobId,
                String workerId,
                Instant failedAt,
                String errorMessage) {
            assertEquals("worker-1", workerId);
            assertEquals(NOW, failedAt);
            assertFalseBlank(errorMessage);
            failedJobs.add(jobId);
            failureDecision = AnalysisFailureDecision.deadLetter();
        }

        private static void assertFalseBlank(String value) {
            assertNotNull(value);
            assertFalse(value.isBlank());
        }
    }
}
