package dev.vericov.upload.api;

import dev.vericov.upload.application.InMemoryUploadRepository;
import dev.vericov.upload.application.AnalysisJob;
import dev.vericov.upload.application.CoverageMetricDetails;
import dev.vericov.upload.application.CoverageReportDetails;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.UploadApplicationService;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.UploadArtifactInput;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UploadResourceTest {

    @Test
    void returnsAcceptedEnvelopeForDirectUpload() {
        UploadResource resource = new UploadResource(service());
        CreateUploadHttpRequest request = request(UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"));

        Response response = resource.createUpload("Bearer vc_live_test", "request-1", request);

        assertEquals(202, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        CreateUploadHttpResponse body = assertInstanceOf(CreateUploadHttpResponse.class, envelope.data());
        assertEquals("queued", body.status());
        assertEquals("/api/v1/uploads/" + body.uploadId(), body.pollUrl());
    }

    @Test
    void returnsResolvedRepositoryIdWhenRequestOmitsRepositoryId() {
        UploadResource resource = new UploadResource(service());

        Response response = resource.createUpload("Bearer vc_live_test", "request-2", request(null));

        assertEquals(202, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        CreateUploadHttpResponse body = assertInstanceOf(CreateUploadHttpResponse.class, envelope.data());
        assertEquals(UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"), body.repositoryId());
    }

    @Test
    void returnsAnalyzedCoverageReportEnvelope() {
        CoverageReportRepository repository = new CoverageReportRepository();
        UploadResource resource = new UploadResource(service(repository));
        Response create = resource.createUpload("Bearer vc_live_test", "request-report", request(null));
        CreateUploadHttpResponse accepted = assertInstanceOf(
                CreateUploadHttpResponse.class,
                assertInstanceOf(ApiResponse.class, create.getEntity()).data());
        repository.report = new CoverageReportDetails(
                accepted.uploadId(),
                accepted.repositoryId(),
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
                Instant.parse("2026-05-22T10:05:00Z"));

        Response response = resource.getCoverageReport("Bearer vc_live_test", accepted.uploadId());

        assertEquals(200, response.getStatus());
        CoverageReportHttpResponse body = assertInstanceOf(
                CoverageReportHttpResponse.class,
                assertInstanceOf(ApiResponse.class, response.getEntity()).data());
        assertEquals("complete", body.status());
        assertEquals(8, body.line().covered());
        assertEquals("coverage-normalized", body.normalizedStorageBucket());
    }

    private static UploadApplicationService service() {
        return service(new InMemoryUploadRepository());
    }

    private static UploadApplicationService service(UploadRepository repository) {
        RepositoryApiKeyAuthenticator authenticator = command -> new RepositoryApiKeyPrincipal(
                UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf"),
                UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"),
                UUID.fromString("9f66fbf9-512e-4de1-94c2-dfca2c18e72b"),
                Set.of("uploads:create", "uploads:read"),
                Set.of("main"));
        ArtifactStorage storage = (tenantId, uploadId, artifact) -> new StoredArtifact(
                artifact.name(),
                artifact.kind(),
                artifact.format(),
                artifact.contentType(),
                artifact.content().length,
                "coverage-raw",
                tenantId + "/" + uploadId + "/" + artifact.name(),
                "sha256-test");
        UploadEventPublisher publisher = event -> {
        };
        UploadWorkQueue queue = upload -> new AnalysisJob(
                UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1"),
                upload.uploadId(),
                upload.repositoryId(),
                upload.commitSha());

        return new UploadApplicationService(
                authenticator,
                repository,
                storage,
                publisher,
                queue,
                Clock.fixed(Instant.parse("2026-05-22T10:00:00Z"), ZoneOffset.UTC));
    }

    private static final class CoverageReportRepository extends InMemoryUploadRepository {
        private CoverageReportDetails report;

        @Override
        public Optional<CoverageReportDetails> coverageReportFor(UUID uploadId) {
            return Optional.ofNullable(report).filter(value -> value.uploadId().equals(uploadId));
        }
    }

    private static CreateUploadHttpRequest request(UUID repositoryId) {
        return new CreateUploadHttpRequest(
                repositoryId,
                "abc123",
                "main",
                42,
                "github_actions",
                "987654321",
                "https://github.com/acme/payments-api/actions/runs/987654321",
                List.of("unit"),
                List.of(),
                "api",
                "services/api",
                List.of(new UploadArtifactHttpRequest(
                        "lcov.info",
                        "coverage",
                        "lcov",
                        "text/plain",
                        "VE46ClNGOnNyYy9NYWluLmphdmEK")));
    }
}
