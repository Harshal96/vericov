package dev.vericov.upload.application;

import dev.vericov.componentconfig.ComponentConfigJson;
import dev.vericov.componentconfig.ComponentConfigSnapshot;
import dev.vericov.componentconfig.ComponentDefinition;
import dev.vericov.componentconfig.ComponentGates;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.UploadArtifactInput;
import dev.vericov.upload.domain.UploadStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID API_KEY_ID = UUID.fromString("9f66fbf9-512e-4de1-94c2-dfca2c18e72b");
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void acceptsUploadStoresArtifactsAndQueuesProcessing() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand command = command("idempotency-1");

        var accepted = fixture.service.acceptUpload(command);

        assertEquals(UploadStatus.QUEUED, accepted.status());
        assertEquals(REPOSITORY_ID, accepted.repositoryId());
        assertEquals("abc123", accepted.commitSha());
        assertEquals("/api/v1/uploads/" + accepted.uploadId(), accepted.pollUrl());
        assertNotNull(accepted.analysisJobId());

        var storedUpload = fixture.uploadRepository.findById(accepted.uploadId()).orElseThrow();
        assertEquals(TENANT_ID, storedUpload.tenantId());
        assertEquals(API_KEY_ID, storedUpload.apiKeyId().orElseThrow());
        assertEquals(UploadStatus.QUEUED, storedUpload.status());
        assertEquals(NOW, storedUpload.acceptedAt());
        assertEquals(List.of("generated/**", "!generated/maintained/**"), storedUpload.ignore());
        assertEquals(
                ComponentConfigJson.sha256(storedUpload.configSnapshot()),
                storedUpload.configSha256());

        assertEquals(2, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(1, fixture.eventPublisher.events.size());
        assertEquals("upload.received", fixture.eventPublisher.events.getFirst().eventType());
        assertEquals(1, fixture.workQueue.jobs.size());
        assertEquals(accepted.analysisJobId(), fixture.workQueue.jobs.getFirst().jobId());
    }

    @Test
    void acceptsUploadWithoutRepositoryIdWhenApiKeyIsRepoScoped() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand command = command("repo-inferred", null);

        var accepted = fixture.service.acceptUpload(command);

        assertEquals(REPOSITORY_ID, accepted.repositoryId());
        var storedUpload = fixture.uploadRepository.findById(accepted.uploadId()).orElseThrow();
        assertEquals(REPOSITORY_ID, storedUpload.repositoryId());
        assertEquals(1, fixture.workQueue.jobs.size());
    }

    @Test
    void rejectsExplicitRepositoryMismatchEvenWhenApiKeyIsRepoScoped() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand command = command(
                "repo-mismatch",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> fixture.service.acceptUpload(command));

        assertEquals("forbidden", exception.code());
        assertEquals(0, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(0, fixture.workQueue.jobs.size());
    }

    @Test
    void returnsExistingUploadWhenIdempotencyKeyWasAlreadyAccepted() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand command = command("same-key");

        var first = fixture.service.acceptUpload(command);
        var second = fixture.service.acceptUpload(command);

        assertEquals(first.uploadId(), second.uploadId());
        assertEquals(first.analysisJobId(), second.analysisJobId());
        assertEquals(2, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(1, fixture.eventPublisher.events.size());
        assertEquals(1, fixture.workQueue.jobs.size());
    }

    @Test
    void returnsConcurrentWinnerWhenDatabaseIdempotencyConstraintWinsRace() {
        TestFixture fixture = new TestFixture();
        fixture.uploadRepository.simulateConcurrentWinner = true;

        var accepted = fixture.service.acceptUpload(command("raced-key"));

        assertEquals(FakeUploadRepository.CONCURRENT_UPLOAD_ID, accepted.uploadId());
        assertEquals(FakeUploadRepository.CONCURRENT_JOB_ID, accepted.analysisJobId());
        assertEquals(0, fixture.eventPublisher.events.size());
        assertEquals(1, fixture.workQueue.jobs.size());
    }

    @Test
    void authenticatesIdempotentRetriesBeforeReturningExistingUpload() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand command = command("same-key-auth");

        fixture.service.acceptUpload(command);
        fixture.authenticator.principal = new RepositoryApiKeyPrincipal(
                TENANT_ID,
                REPOSITORY_ID,
                API_KEY_ID,
                Set.of("uploads:read"),
                Set.of("main"));

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> fixture.service.acceptUpload(command));

        assertEquals("forbidden", exception.code());
        assertEquals(2, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(1, fixture.eventPublisher.events.size());
        assertEquals(1, fixture.workQueue.jobs.size());
    }

    @Test
    void rejectsUploadWhenApiKeyDoesNotHaveCreateScope() {
        TestFixture fixture = new TestFixture();
        fixture.authenticator.principal = new RepositoryApiKeyPrincipal(
                TENANT_ID,
                REPOSITORY_ID,
                API_KEY_ID,
                Set.of("uploads:read"),
                Set.of("main"));

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> fixture.service.acceptUpload(command("scope-failure")));

        assertEquals("forbidden", exception.code());
        assertEquals(0, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(0, fixture.eventPublisher.events.size());
        assertEquals(0, fixture.workQueue.jobs.size());
    }

    @Test
    void returnsUploadStatusWithStoredArtifactMetadata() {
        TestFixture fixture = new TestFixture();
        var accepted = fixture.service.acceptUpload(command("status-key"));

        var status = fixture.service.getUpload(accepted.uploadId());

        assertEquals(accepted.uploadId(), status.uploadId());
        assertEquals(UploadStatus.QUEUED, status.status());
        assertEquals(2, status.artifacts().size());
        assertEquals("lcov.info", status.artifacts().getFirst().name());
    }

    @Test
    void returnsCoverageReportForAuthorizedUpload() {
        TestFixture fixture = new TestFixture();
        var accepted = fixture.service.acceptUpload(command("report-key"));
        fixture.uploadRepository.coverageReport = new CoverageReportDetails(
                accepted.uploadId(),
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                "complete",
                new CoverageMetricDetails(8, 10),
                new CoverageMetricDetails(1, 2),
                new CoverageMetricDetails(3, 4),
                new CoverageMetricDetails(8, 10),
                "coverage-normalized",
                "report.json.gz",
                NOW);

        var report = fixture.service.getCoverageReport(accepted.uploadId(), "Bearer vc_live_test");

        assertEquals(8, report.line().covered());
        assertEquals(10, report.line().total());
    }

    @Test
    void rejectsArtifactNamesThatCouldEscapeObjectPrefix() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand command = new CreateUploadCommand(
                "Bearer vc_live_test",
                "unsafe-artifact-name",
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                "github_actions",
                "987654321",
                "https://github.com/acme/payments-api/actions/runs/987654321",
                List.of("unit", "linux"),
                List.of(),
                Optional.of("api"),
                Optional.of("services/api"),
                List.of(new UploadArtifactInput(
                        "../lcov.info",
                        ArtifactKind.COVERAGE,
                        "lcov",
                        "text/plain",
                        "TN:\n".getBytes(StandardCharsets.UTF_8))));

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> fixture.service.acceptUpload(command));

        assertEquals("validation_error", exception.code());
        assertEquals(0, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(0, fixture.workQueue.jobs.size());
    }

    @Test
    void rejectsInvalidIgnoreRulesBeforeAuthenticationOrSideEffects() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand valid = command("invalid-ignore");
        CreateUploadCommand command = new CreateUploadCommand(
                valid.authorizationHeader(),
                valid.idempotencyKey(),
                valid.repositoryId(),
                valid.commitSha(),
                valid.branch(),
                valid.pullRequestNumber(),
                valid.ciProvider(),
                valid.ciBuildId(),
                valid.ciBuildUrl(),
                valid.flags(),
                List.of("generated/**", "../secret.py"),
                valid.component(),
                valid.packageName(),
                valid.artifacts());

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> fixture.service.acceptUpload(command));

        assertEquals("validation_error", exception.code());
        assertEquals(0, fixture.authenticator.calls);
        assertEquals(0, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(0, fixture.workQueue.jobs.size());
    }

    @Test
    void acceptsComponentSnapshotAndPersistsCanonicalHash() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand valid = command("components");
        ComponentDefinition component = new ComponentDefinition(
                "api",
                "API",
                List.of("team-api"),
                new ComponentGates(Map.of("line", java.math.BigDecimal.valueOf(90))),
                List.of("services/api/**"),
                List.of());
        ComponentConfigSnapshot snapshot = new ComponentConfigSnapshot(
                1,
                valid.ignore(),
                List.of(component));
        CreateUploadCommand command = valid.withConfig(
                snapshot.components(),
                ComponentConfigJson.sha256(snapshot),
                true);

        var accepted = fixture.service.acceptUpload(command);

        QueuedUpload stored = fixture.uploadRepository.findById(accepted.uploadId()).orElseThrow();
        assertEquals("api", stored.configSnapshot().components().getFirst().key());
        assertEquals(ComponentConfigJson.canonicalJson(snapshot), stored.configSnapshotJson());
        assertEquals(ComponentConfigJson.sha256(snapshot), stored.configSha256());
    }

    @Test
    void rejectsConfigHashMismatchBeforeAuthenticationOrSideEffects() {
        TestFixture fixture = new TestFixture();
        CreateUploadCommand command = command("hash-mismatch")
                .withConfig(List.of(), "0".repeat(64), true);

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> fixture.service.acceptUpload(command));

        assertEquals("validation_error", exception.code());
        assertEquals(0, fixture.authenticator.calls);
        assertEquals(0, fixture.artifactStorage.storedArtifacts.size());
        assertEquals(0, fixture.workQueue.jobs.size());
    }

    private static CreateUploadCommand command(String idempotencyKey) {
        return command(idempotencyKey, REPOSITORY_ID);
    }

    private static CreateUploadCommand command(String idempotencyKey, UUID repositoryId) {
        return new CreateUploadCommand(
                "Bearer vc_live_test",
                idempotencyKey,
                repositoryId,
                "abc123",
                "main",
                42,
                "github_actions",
                "987654321",
                "https://github.com/acme/payments-api/actions/runs/987654321",
                List.of("unit", "linux"),
                List.of("generated/**", "!generated/maintained/**"),
                Optional.of("api"),
                Optional.of("services/api"),
                List.of(
                        new UploadArtifactInput(
                                "lcov.info",
                                ArtifactKind.COVERAGE,
                                "lcov",
                                "text/plain",
                                "TN:\nSF:src/Main.java\n".getBytes(StandardCharsets.UTF_8)),
                        new UploadArtifactInput(
                                "junit.xml",
                                ArtifactKind.TEST_RESULTS,
                                "junit",
                                "application/xml",
                                "<testsuite/>".getBytes(StandardCharsets.UTF_8))));
    }

    private static final class TestFixture {
        private final FakeAuthenticator authenticator = new FakeAuthenticator();
        private final FakeUploadRepository uploadRepository = new FakeUploadRepository();
        private final FakeArtifactStorage artifactStorage = new FakeArtifactStorage();
        private final FakeEventPublisher eventPublisher = new FakeEventPublisher();
        private final FakeWorkQueue workQueue = new FakeWorkQueue();
        private final UploadApplicationService service = new UploadApplicationService(
                authenticator,
                uploadRepository,
                artifactStorage,
                eventPublisher,
                workQueue,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FakeAuthenticator implements RepositoryApiKeyAuthenticator {
        private RepositoryApiKeyPrincipal principal = new RepositoryApiKeyPrincipal(
                TENANT_ID,
                REPOSITORY_ID,
                API_KEY_ID,
                Set.of("uploads:create", "uploads:read"),
                Set.of("main"));
        private int calls;

        @Override
        public RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command) {
            calls++;
            return principal;
        }
    }

    private static final class FakeUploadRepository extends InMemoryUploadRepository {
        private static final UUID CONCURRENT_UPLOAD_ID =
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID CONCURRENT_JOB_ID =
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        private boolean simulateConcurrentWinner;
        private CoverageReportDetails coverageReport;

        @Override
        public void save(QueuedUpload upload, List<StoredArtifact> artifacts) {
            if (!simulateConcurrentWinner) {
                super.save(upload, artifacts);
                return;
            }
            simulateConcurrentWinner = false;
            super.save(new QueuedUpload(
                    CONCURRENT_UPLOAD_ID,
                    upload.tenantId(),
                    upload.repositoryId(),
                    upload.apiKeyId(),
                    upload.commitSha(),
                    upload.branch(),
                    upload.pullRequestNumber(),
                    upload.ciProvider(),
                    upload.ciBuildId(),
                    upload.ciBuildUrl(),
                    upload.flags(),
                    upload.ignore(),
                    upload.component(),
                    upload.packageName(),
                    upload.status(),
                    upload.idempotencyKey(),
                    upload.acceptedAt(),
                    Optional.of(CONCURRENT_JOB_ID)), artifacts);
            throw new DuplicateUploadException(new IllegalStateException("duplicate"));
        }

        @Override
        public Optional<CoverageReportDetails> coverageReportFor(UUID uploadId) {
            return Optional.ofNullable(coverageReport)
                    .filter(report -> report.uploadId().equals(uploadId));
        }
    }

    private static final class FakeArtifactStorage implements ArtifactStorage {
        private final List<StoredArtifact> storedArtifacts = new ArrayList<>();

        @Override
        public StoredArtifact store(UUID tenantId, UUID uploadId, UploadArtifactInput artifact) {
            StoredArtifact stored = new StoredArtifact(
                    artifact.name(),
                    artifact.kind(),
                    artifact.format(),
                    artifact.contentType(),
                    artifact.content().length,
                    "coverage-raw",
                    tenantId + "/" + uploadId + "/" + artifact.name(),
                    "sha256-test");
            storedArtifacts.add(stored);
            return stored;
        }
    }

    private static final class FakeEventPublisher implements UploadEventPublisher {
        private final List<UploadEvent> events = new ArrayList<>();

        @Override
        public void publish(UploadEvent event) {
            events.add(event);
        }
    }

    private static final class FakeWorkQueue implements UploadWorkQueue {
        private final List<AnalysisJob> jobs = new ArrayList<>();

        @Override
        public AnalysisJob enqueueAnalysis(QueuedUpload upload) {
            AnalysisJob job = new AnalysisJob(
                    UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1"),
                    upload.uploadId(),
                    upload.repositoryId(),
                    upload.commitSha());
            jobs.add(job);
            return job;
        }
    }
}
