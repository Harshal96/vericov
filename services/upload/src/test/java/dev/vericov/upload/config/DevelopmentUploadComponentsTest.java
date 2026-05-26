package dev.vericov.upload.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.upload.application.InMemoryUploadRepository;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.UploadEvent;
import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.UploadArtifactInput;
import dev.vericov.upload.domain.UploadStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DevelopmentUploadComponentsTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPOSITORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID UPLOAD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void defaultsToInMemoryRepositoryWhenNoDatabaseIsConfigured() {
        assumeDefaultUploadEnvironment();
        DevelopmentUploadComponents components = new DevelopmentUploadComponents();

        assertInstanceOf(InMemoryUploadRepository.class, components.uploadRepository());
    }

    @Test
    void inMemoryArtifactStoragePersistsArtifactMetadataAndContentDigest() {
        assumeDefaultUploadEnvironment();
        DevelopmentUploadComponents components = new DevelopmentUploadComponents();

        var artifact = components.artifactStorage().store(
                TENANT_ID,
                UPLOAD_ID,
                new UploadArtifactInput(
                        "coverage.lcov",
                        ArtifactKind.COVERAGE,
                        "lcov",
                        "text/plain",
                        "TN:\nend_of_record\n".getBytes(StandardCharsets.UTF_8)));

        assertEquals("coverage.lcov", artifact.name());
        assertEquals("coverage-raw", artifact.storageBucket());
        assertEquals(TENANT_ID + "/" + UPLOAD_ID + "/coverage.lcov", artifact.storagePath());
        assertEquals(18L, artifact.sizeBytes());
        assertNotNull(artifact.sha256());
    }

    @Test
    void inMemoryPublisherAndQueueAcceptLocalUploadEvents() {
        assumeDefaultUploadEnvironment();
        DevelopmentUploadComponents components = new DevelopmentUploadComponents();

        assertDoesNotThrow(() -> components.uploadEventPublisher().publish(new UploadEvent(
                UUID.randomUUID(),
                TENANT_ID,
                UPLOAD_ID,
                "upload.received",
                Map.of("commit_sha", "abc123"),
                Instant.parse("2026-05-22T10:15:30Z"))));

        var job = components.uploadWorkQueue().enqueueAnalysis(new QueuedUpload(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                Optional.empty(),
                "abc123",
                "main",
                42,
                "github-actions",
                "build-1",
                "https://ci.example/build-1",
                List.of("unit"),
                Optional.empty(),
                Optional.empty(),
                UploadStatus.ACCEPTED,
                "key-1",
                Instant.parse("2026-05-22T10:15:30Z"),
                Optional.empty()));

        assertEquals(UPLOAD_ID, job.uploadId());
        assertEquals(REPOSITORY_ID, job.repositoryId());
        assertEquals("abc123", job.commitSha());
    }

    @Test
    void environmentApiKeyAuthenticatorFailsClosedWithoutDevApiKey() {
        assumeDefaultUploadEnvironment();
        Assumptions.assumeTrue(env("VERICOV_DEV_API_KEY").isBlank());
        DevelopmentUploadComponents components = new DevelopmentUploadComponents();

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> components.repositoryApiKeyAuthenticator().authenticate(new CreateUploadCommand(
                        "Bearer presented",
                        "idempotency-key",
                        REPOSITORY_ID,
                        "abc123",
                        "main",
                        null,
                        "github-actions",
                        "build-1",
                        "https://ci.example/build-1",
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of())));

        assertEquals("unauthorized", exception.code());
    }

    private static void assumeDefaultUploadEnvironment() {
        Assumptions.assumeTrue(env("VERICOV_UPLOAD_DB_URL").isBlank()
                && env("SUPABASE_DB_URL").isBlank()
                && env("VERICOV_ARTIFACT_STORAGE_BACKEND").isBlank()
                && env("SUPABASE_SERVICE_ROLE_KEY").isBlank());
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
