package dev.vericov.upload.api;

import dev.vericov.upload.application.AnalysisJob;
import dev.vericov.upload.application.InMemoryUploadRepository;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.UploadApplicationService;
import dev.vericov.upload.application.UploadEvent;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.UploadArtifactInput;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadResourceIntegrationTest {
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID API_KEY_ID = UUID.fromString("9f66fbf9-512e-4de1-94c2-dfca2c18e72b");
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void acceptsUploadAndExposesStatusAndArtifactMetadata() {
        Fixture fixture = new Fixture();

        Response create = fixture.resource.createUpload("Bearer vc_live_test", "integration-1", validRequest());

        assertEquals(202, create.getStatus());
        CreateUploadHttpResponse accepted = acceptedBody(create);
        Response statusResponse = fixture.resource.getUpload(accepted.uploadId());
        Response artifactsResponse = fixture.resource.getArtifacts(accepted.uploadId());

        assertEquals(200, statusResponse.getStatus());
        UploadStatusHttpResponse status = responseBody(statusResponse, UploadStatusHttpResponse.class);
        assertEquals(accepted.uploadId(), status.id());
        assertEquals("queued", status.status());
        assertEquals(2, status.artifacts().size());
        assertEquals(NOW, status.createdAt());

        assertEquals(200, artifactsResponse.getStatus());
        List<?> artifacts = responseBody(artifactsResponse, List.class);
        assertEquals(2, artifacts.size());
        assertEquals(2, fixture.storage.storedArtifacts.size());
        assertEquals(1, fixture.queue.jobs.size());
        assertEquals(1, fixture.publisher.events.size());
    }

    @Test
    void returnsValidationEnvelopeForMalformedArtifactContent() {
        Fixture fixture = new Fixture();
        CreateUploadHttpRequest request = new CreateUploadHttpRequest(
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                "github_actions",
                "987654321",
                "https://github.com/acme/payments-api/actions/runs/987654321",
                List.of("unit"),
                "api",
                "services/api",
                List.of(new UploadArtifactHttpRequest(
                        "lcov.info",
                        "coverage",
                        "lcov",
                        "text/plain",
                        "not base64")));

        Response response = fixture.resource.createUpload("Bearer vc_live_test", "integration-2", request);

        assertEquals(400, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("validation_error", error.error().code());
        assertTrue(fixture.storage.storedArtifacts.isEmpty());
        assertTrue(fixture.queue.jobs.isEmpty());
        assertTrue(fixture.publisher.events.isEmpty());
    }

    @Test
    void returnsNotFoundEnvelopeForUnknownUpload() {
        Fixture fixture = new Fixture();

        Response response = fixture.resource.getUpload(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("not_found", error.error().code());
    }

    @Test
    void returnsServiceUnavailableWhenArtifactStorageFails() {
        Fixture fixture = new Fixture();
        fixture.storage.failure = new IllegalStateException("Supabase Storage upload failed with HTTP 500");

        Response response = fixture.resource.createUpload("Bearer vc_live_test", "integration-3", validRequest());

        assertEquals(503, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("artifact_storage_unavailable", error.error().code());
        assertTrue(fixture.queue.jobs.isEmpty());
        assertTrue(fixture.publisher.events.isEmpty());
    }

    private static CreateUploadHttpRequest validRequest() {
        return new CreateUploadHttpRequest(
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                "github_actions",
                "987654321",
                "https://github.com/acme/payments-api/actions/runs/987654321",
                List.of("unit", "linux"),
                "api",
                "services/api",
                List.of(
                        new UploadArtifactHttpRequest(
                                "lcov.info",
                                "coverage",
                                "lcov",
                                "text/plain",
                                "VE46ClNGOnNyYy9NYWluLmphdmEK"),
                        new UploadArtifactHttpRequest(
                                "junit.xml",
                                "test_results",
                                "junit",
                                "application/xml",
                                "PHRlc3RzdWl0ZS8+")));
    }

    private static CreateUploadHttpResponse acceptedBody(Response response) {
        return responseBody(response, CreateUploadHttpResponse.class);
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        return assertInstanceOf(type, envelope.data());
    }

    private static final class Fixture {
        private final FakeStorage storage = new FakeStorage();
        private final FakePublisher publisher = new FakePublisher();
        private final FakeQueue queue = new FakeQueue();
        private final UploadResource resource = new UploadResource(new UploadApplicationService(
                new FakeAuthenticator(),
                new InMemoryUploadRepository(),
                storage,
                publisher,
                queue,
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static final class FakeAuthenticator implements RepositoryApiKeyAuthenticator {
        @Override
        public RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command) {
            return new RepositoryApiKeyPrincipal(
                    TENANT_ID,
                    REPOSITORY_ID,
                    API_KEY_ID,
                    Set.of("uploads:create", "uploads:read"),
                    Set.of("main"));
        }
    }

    private static final class FakeStorage implements ArtifactStorage {
        private final List<StoredArtifact> storedArtifacts = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public StoredArtifact store(UUID tenantId, UUID uploadId, UploadArtifactInput artifact) {
            if (failure != null) {
                throw failure;
            }
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

    private static final class FakePublisher implements UploadEventPublisher {
        private final List<UploadEvent> events = new ArrayList<>();

        @Override
        public void publish(UploadEvent event) {
            events.add(event);
        }
    }

    private static final class FakeQueue implements UploadWorkQueue {
        private final List<AnalysisJob> jobs = new ArrayList<>();

        @Override
        public AnalysisJob enqueueAnalysis(dev.vericov.upload.application.QueuedUpload upload) {
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
