package dev.vericov.upload.bdd.steps;

import dev.vericov.upload.api.ApiError;
import dev.vericov.upload.api.ApiResponse;
import dev.vericov.upload.api.CreateRunnerUploadTokenHttpRequest;
import dev.vericov.upload.api.CreateUploadHttpRequest;
import dev.vericov.upload.api.CreateUploadHttpResponse;
import dev.vericov.upload.api.RunnerUploadTokenHttpResponse;
import dev.vericov.upload.api.UploadArtifactHttpRequest;
import dev.vericov.upload.api.UploadResource;
import dev.vericov.upload.api.UploadStatusHttpResponse;
import dev.vericov.upload.application.AnalysisJob;
import dev.vericov.upload.application.InMemoryUploadRepository;
import dev.vericov.upload.application.RunnerUploadToken;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.UploadApplicationService;
import dev.vericov.upload.application.UploadEvent;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.RunnerUploadTokenIssuer;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.UploadArtifactInput;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UploadSteps {
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID API_KEY_ID = UUID.fromString("9f66fbf9-512e-4de1-94c2-dfca2c18e72b");
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    private TestFixture fixture;
    private CreateUploadHttpRequest request;
    private Response firstResponse;
    private Response latestResponse;
    private UUID firstUploadId;
    private UUID latestUploadId;
    private String branch = "main";

    @Given("repository {string} accepts uploads on branch {string}")
    public void repositoryAcceptsUploadsOnBranch(String repositoryName, String branchName) {
        branch = branchName;
        fixture = new TestFixture(Set.of("uploads:create", "uploads:read"), Set.of(branchName));
        assertFalse(repositoryName.isBlank());
    }

    @Given("the API key only has upload read scope")
    public void apiKeyOnlyHasUploadReadScope() {
        fixture.setPrincipal(Set.of("uploads:read"), Set.of(branch));
    }

    @Given("the API key only allows branch {string}")
    public void apiKeyOnlyAllowsBranch(String allowedBranch) {
        fixture.setPrincipal(Set.of("uploads:create", "uploads:read"), Set.of(allowedBranch));
    }

    @Given("the upload request includes coverage and test-result artifacts")
    public void uploadRequestIncludesCoverageAndTestResultArtifacts() {
        request = uploadRequest(branch, List.of(
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

    @Given("the upload request includes an artifact named {string}")
    public void uploadRequestIncludesArtifactNamed(String artifactName) {
        request = uploadRequest(branch, List.of(new UploadArtifactHttpRequest(
                artifactName,
                "coverage",
                "lcov",
                "text/plain",
                "VE46Cg==")));
    }

    @When("the repository submits the upload with idempotency key {string}")
    public void repositorySubmitsUploadWithIdempotencyKey(String idempotencyKey) {
        latestResponse = fixture.resource.createUpload("Bearer vc_live_test", idempotencyKey, request);
        latestUploadId = uploadIdFrom(latestResponse);
        if (firstResponse == null) {
            firstResponse = latestResponse;
            firstUploadId = latestUploadId;
        }
    }

    @When("the repository submits the upload with idempotency key {string} again")
    public void repositorySubmitsUploadWithIdempotencyKeyAgain(String idempotencyKey) {
        repositorySubmitsUploadWithIdempotencyKey(idempotencyKey);
    }

    @Then("the API accepts the upload")
    public void apiAcceptsUpload() {
        assertEquals(202, latestResponse.getStatus());
        CreateUploadHttpResponse body = acceptedBody(latestResponse);
        assertEquals("queued", body.status());
        assertEquals(REPOSITORY_ID, body.repositoryId());
        assertEquals("abc123", body.commitSha());
        assertNotNull(body.analysisJobId());
    }

    @Then("the response contains a poll URL")
    public void responseContainsPollUrl() {
        CreateUploadHttpResponse body = acceptedBody(latestResponse);
        assertEquals("/api/v1/uploads/" + body.uploadId(), body.pollUrl());
    }

    @Then("upload status lists {int} stored artifacts")
    public void uploadStatusListsStoredArtifacts(int artifactCount) {
        Response response = fixture.resource.getUpload("Bearer vc_live_test", latestUploadId);
        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        UploadStatusHttpResponse status = assertInstanceOf(UploadStatusHttpResponse.class, envelope.data());
        assertEquals(latestUploadId, status.id());
        assertEquals("queued", status.status());
        assertEquals(artifactCount, status.artifacts().size());
    }

    @Then("artifact metadata lists {int} stored artifacts")
    public void artifactMetadataListsStoredArtifacts(int artifactCount) {
        Response response = fixture.resource.getArtifacts("Bearer vc_live_test", latestUploadId);
        assertEquals(200, response.getStatus());
        List<?> artifacts = responseBody(response, List.class);
        assertEquals(artifactCount, artifacts.size());
        assertInstanceOf(dev.vericov.upload.api.UploadArtifactHttpResponse.class, artifacts.getFirst());
    }

    @When("the repository requests a runner upload token for branch {string}")
    public void repositoryRequestsRunnerUploadTokenForBranch(String requestedBranch) {
        latestResponse = fixture.resource.createRunnerUploadToken(
                "Bearer vc_live_test",
                new CreateRunnerUploadTokenHttpRequest(REPOSITORY_ID, requestedBranch));
    }

    @Then("the API returns a runner upload token")
    public void apiReturnsRunnerUploadToken() {
        assertEquals(200, latestResponse.getStatus());
        RunnerUploadTokenHttpResponse token = responseBody(latestResponse, RunnerUploadTokenHttpResponse.class);
        assertEquals("runner-token-main", token.token());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), token.expiresAt());
        assertEquals(REPOSITORY_ID, fixture.runnerTokenIssuer.repositoryId);
        assertEquals("main", fixture.runnerTokenIssuer.branch);
        assertEquals(Duration.ofMinutes(15), fixture.runnerTokenIssuer.ttl);
    }

    @Then("coverage analysis is queued once")
    public void coverageAnalysisIsQueuedOnce() {
        assertEquals(1, fixture.queue.jobs.size());
        assertEquals(latestUploadId, fixture.queue.jobs.getFirst().uploadId());
    }

    @Then("an upload received event is published once")
    public void uploadReceivedEventIsPublishedOnce() {
        assertEquals(1, fixture.publisher.events.size());
        UploadEvent event = fixture.publisher.events.getFirst();
        assertEquals("upload.received", event.eventType());
        assertEquals(latestUploadId, event.uploadId());
        assertTrue(event.payload().containsKey("analysis_job_id"));
    }

    @Then("the repeated upload returns the same upload id")
    public void repeatedUploadReturnsSameUploadId() {
        assertEquals(firstUploadId, latestUploadId);
        assertEquals(firstUploadId, acceptedBody(firstResponse).uploadId());
    }

    @Then("upload artifacts are stored once")
    public void uploadArtifactsAreStoredOnce() {
        assertEquals(2, fixture.storage.storedArtifacts.size());
    }

    @Then("the API rejects the upload with status {int} and code {string}")
    public void apiRejectsUploadWithStatusAndCode(int statusCode, String code) {
        assertEquals(statusCode, latestResponse.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, latestResponse.getEntity());
        assertEquals(code, error.error().code());
        assertFalse(error.error().message().isBlank());
    }

    @Then("no upload side effects are recorded")
    public void noUploadSideEffectsAreRecorded() {
        assertTrue(fixture.storage.storedArtifacts.isEmpty());
        assertTrue(fixture.queue.jobs.isEmpty());
        assertTrue(fixture.publisher.events.isEmpty());
    }

    private static CreateUploadHttpRequest uploadRequest(String branch, List<UploadArtifactHttpRequest> artifacts) {
        return new CreateUploadHttpRequest(
                REPOSITORY_ID,
                "abc123",
                branch,
                42,
                null,
                "github_actions",
                "987654321",
                "https://github.com/acme/payments-api/actions/runs/987654321",
                List.of("unit", "linux"),
                List.of(),
                null,
                null,
                "api",
                "services/api",
                artifacts);
    }

    private static UUID uploadIdFrom(Response response) {
        if (response.getStatus() != 202) {
            return null;
        }
        return acceptedBody(response).uploadId();
    }

    private static CreateUploadHttpResponse acceptedBody(Response response) {
        return responseBody(response, CreateUploadHttpResponse.class);
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        return assertInstanceOf(type, envelope.data());
    }

    private static final class TestFixture {
        private final FakeAuthenticator authenticator;
        private final FakeStorage storage = new FakeStorage();
        private final FakePublisher publisher = new FakePublisher();
        private final FakeQueue queue = new FakeQueue();
        private final FakeRunnerTokenIssuer runnerTokenIssuer = new FakeRunnerTokenIssuer();
        private final UploadResource resource;

        private TestFixture(Set<String> scopes, Set<String> allowedBranches) {
            authenticator = new FakeAuthenticator(scopes, allowedBranches);
            resource = new UploadResource(new UploadApplicationService(
                    authenticator,
                    new InMemoryUploadRepository(),
                    storage,
                    publisher,
                    queue,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    runnerTokenIssuer));
        }

        private void setPrincipal(Set<String> scopes, Set<String> allowedBranches) {
            authenticator.principal = principal(scopes, allowedBranches);
        }
    }

    private static final class FakeAuthenticator implements RepositoryApiKeyAuthenticator {
        private RepositoryApiKeyPrincipal principal;

        private FakeAuthenticator(Set<String> scopes, Set<String> allowedBranches) {
            principal = principal(scopes, allowedBranches);
        }

        @Override
        public RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command) {
            return principal;
        }
    }

    private static RepositoryApiKeyPrincipal principal(Set<String> scopes, Set<String> allowedBranches) {
        return new RepositoryApiKeyPrincipal(
                TENANT_ID,
                REPOSITORY_ID,
                API_KEY_ID,
                scopes,
                allowedBranches);
    }

    private static final class FakeStorage implements ArtifactStorage {
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

    private static final class FakeRunnerTokenIssuer implements RunnerUploadTokenIssuer {
        private UUID repositoryId;
        private String branch;
        private Duration ttl;

        @Override
        public RunnerUploadToken issue(
                RepositoryApiKeyPrincipal principal,
                UUID repositoryId,
                String branch,
                Duration ttl) {
            this.repositoryId = repositoryId;
            this.branch = branch;
            this.ttl = ttl;
            return new RunnerUploadToken("runner-token-" + branch, NOW.plus(ttl));
        }
    }
}
