package dev.vericov.upload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.vericov.upload.application.CoverageFileDetail;
import dev.vericov.upload.application.CoverageLineRange;
import dev.vericov.upload.application.CoverageMetricDetails;
import dev.vericov.upload.application.CoverageQueryService;
import dev.vericov.upload.application.CoverageReportDetails;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.PatchCoverageDetails;
import dev.vericov.upload.application.ResolvedCoverageRef;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoverageQueryResourceTest {
    private static final UUID REPOSITORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPORT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID UPLOAD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void summaryReturnsOkWithResolvedRefAndMetrics() {
        FakeRepository repository = new FakeRepository();
        repository.report = new CoverageReportDetails(
                UPLOAD_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                null,
                "complete",
                new CoverageMetricDetails(8, 10),
                new CoverageMetricDetails(1, 2),
                new CoverageMetricDetails(1, 1),
                new CoverageMetricDetails(8, 10),
                "bucket",
                "path.json.gz",
                Instant.parse("2026-07-03T00:00:00Z"));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.summary("Bearer vc_repo_test", "main");

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<CoverageSummaryHttpResponse>) response.getEntity();
        assertEquals(8L, body.data().line().covered());
        assertEquals("abc123", body.data().resolved().commitSha());
    }

    @Test
    void summaryReturns404ForUnknownRef() {
        FakeRepository repository = new FakeRepository();
        repository.resolved = Optional.empty();
        CoverageQueryResource resource = resource(repository);

        Response response = resource.summary("Bearer vc_repo_test", "missing");

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("ref_not_found", error.error().code());
    }

    @Test
    void scopeMissingReturns403() {
        FakeRepository repository = new FakeRepository();
        CoverageQueryResource resource = resource(repository, Set.of("uploads:create"));

        Response response = resource.summary("Bearer vc_repo_test", "main");

        assertEquals(403, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("scope_missing", error.error().code());
    }

    @Test
    void fileNotFoundReturns404WithDidYouMeanDetails() {
        FakeRepository repository = new FakeRepository();
        repository.similarPaths = List.of("src/main/Main.java");
        CoverageQueryResource resource = resource(repository);

        Response response = resource.file("Bearer vc_repo_test", "main", "Main.java");

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("file_not_found", error.error().code());
        assertEquals(1, error.error().details().size());
        assertEquals("src/main/Main.java", error.error().details().get(0).message());
    }

    @Test
    void fileReturnsUncoveredRanges() {
        FakeRepository repository = new FakeRepository();
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.file = new CoverageFileDetail(
                "src/Main.java",
                "api",
                List.of("team-api"),
                metric,
                metric,
                metric,
                metric,
                List.of(new CoverageLineRange(3, 4)));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.file("Bearer vc_repo_test", "main", "src/Main.java");

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<CoverageFileHttpResponse>) response.getEntity();
        assertEquals(1, body.data().uncoveredRanges().size());
        assertEquals(3, body.data().uncoveredRanges().get(0).start());
    }

    @Test
    void pullRequestPatchReturns404WhenNoDiffBearingReportExists() {
        FakeRepository repository = new FakeRepository();
        repository.patch = Optional.empty();
        CoverageQueryResource resource = resource(repository);

        Response response = resource.pullRequestPatch("Bearer vc_repo_test", 42);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("pull_request_not_found", error.error().code());
    }

    @Test
    void pullRequestPatchReturnsPatchCoverage() {
        FakeRepository repository = new FakeRepository();
        repository.patch = Optional.of(new PatchCoverageDetails(
                "complete", "base-sha", "head-sha", 4, 5, 0, 1, List.of()));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.pullRequestPatch("Bearer vc_repo_test", 42);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<PatchCoverageHttpResponse>) response.getEntity();
        assertEquals("complete", body.data().status());
    }

    @Test
    void componentsReturnsTheComponentTree() {
        FakeRepository repository = new FakeRepository();
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.report = new CoverageReportDetails(
                UPLOAD_ID, REPOSITORY_ID, "abc123", "main", null, "complete",
                metric, metric, metric, metric,
                "bucket", "path", "sha256", "passed", List.of(),
                List.of(new dev.vericov.upload.application.ComponentCoverageDetails(
                        "api", "API", List.of("api"), 0, 0, List.of("team-api"), java.util.Map.of(),
                        metric, metric, metric, metric, 1, 1, 0, 0,
                        java.math.BigDecimal.ZERO, null, List.of(), List.of())),
                null,
                Instant.parse("2026-07-03T00:00:00Z"));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.components("Bearer vc_repo_test", "main");

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<ComponentCoverageListHttpResponse>) response.getEntity();
        assertEquals(1, body.data().components().size());
        assertEquals("api", body.data().components().get(0).key());
    }

    @Test
    void filesReturnsAPageOfFileSummaries() {
        FakeRepository repository = new FakeRepository();
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.files = List.of(new dev.vericov.upload.application.CoverageFileSummaryDetails(
                "src/Main.java", "api", List.of("team-api"), metric, metric, metric, metric));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.files("Bearer vc_repo_test", "main", null, null, null, null, null);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<CoverageFileListHttpResponse>) response.getEntity();
        assertEquals(1, body.data().files().size());
        assertEquals("src/Main.java", body.data().files().get(0).filePath());
    }

    @Test
    void gapsReturnsAPageOfFindings() {
        FakeRepository repository = new FakeRepository();
        repository.gaps = List.of(new dev.vericov.upload.application.CoverageGapFindingDetails(
                "src/Main.java", "line", 3, 4, null, "uncovered_executable_line", "explanation",
                "high", new java.math.BigDecimal("60.0"), "high", List.of("team-api"), "api", "add_test", "active"));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.gaps("Bearer vc_repo_test", "main", null, null, null, null, null);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<CoverageGapListHttpResponse>) response.getEntity();
        assertEquals(1, body.data().gaps().size());
        assertEquals("add_test", body.data().gaps().get(0).nextAction());
    }

    @Test
    void gatesReturnsGateEvaluations() {
        FakeRepository repository = new FakeRepository();
        repository.gates = List.of(new dev.vericov.upload.application.CoverageGateEvaluationDetails(
                "line-gate", "line_coverage", "line", "repository", null, List.of(),
                new java.math.BigDecimal("80"), new java.math.BigDecimal("75"), "failed", true));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.gates("Bearer vc_repo_test", "main");

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<CoverageGateListHttpResponse>) response.getEntity();
        assertEquals(1, body.data().gates().size());
        assertEquals("failed", body.data().gates().get(0).status());
    }

    @Test
    void gapManifestReturnsAFullyAssembledManifest() {
        FakeRepository repository = new FakeRepository();
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.report = new CoverageReportDetails(
                UPLOAD_ID, REPOSITORY_ID, "abc123", "main", null, "complete",
                metric, metric, metric, metric, "bucket", "path", "sha256", "failed",
                List.of(), List.of(), null, Instant.parse("2026-07-03T00:00:00Z"));
        repository.repositoryInfo = Optional.of(new dev.vericov.upload.application.RepositoryInfo("acme/api", "main"));
        repository.gates = List.of(new dev.vericov.upload.application.CoverageGateEvaluationDetails(
                "line-gate", "line_coverage", "line", "repository", null, List.of(),
                new java.math.BigDecimal("80"), new java.math.BigDecimal("75"), "failed", true));
        repository.manifestEntries = List.of(new dev.vericov.upload.application.CoverageGapManifestEntry(
                UUID.randomUUID(), 1, "src/Retry.java", "range", 84, 97, null, true,
                "new_uncovered_changed_line", "explanation", "high",
                new java.math.BigDecimal("78.0"), "high", List.of("change_exposure: reason (+25)"),
                "payments-api", List.of("team-payments"), "add_test",
                List.of(new CoverageLineRange(84, 91), new CoverageLineRange(95, 97)), false));
        CoverageQueryResource resource = resource(repository);

        Response response = resource.gapManifest("Bearer vc_repo_test", "main", null, null, null, null);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<CoverageGapManifestHttpResponse>) response.getEntity();
        assertEquals(1, body.data().manifestVersion());
        assertEquals("acme/api", body.data().repository().fullName());
        assertEquals("failed", body.data().report().gateStatus());
        assertEquals(1, body.data().failedGates().size());
        assertEquals(1, body.data().entries().size());
        assertEquals("payments-api", body.data().entries().get(0).componentKey());
        assertEquals(2, body.data().entries().get(0).uncoveredRanges().size());
    }

    @Test
    void gapManifestForUnknownPullRequestReturns404() {
        FakeRepository repository = new FakeRepository();
        repository.pullRequestResolved = null;
        CoverageQueryResource resource = resource(repository);

        Response response = resource.gapManifest("Bearer vc_repo_test", null, 999, null, null, null);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("pull_request_not_found", error.error().code());
    }

    private static CoverageQueryResource resource(FakeRepository repository) {
        return resource(repository, Set.of("uploads:read"));
    }

    private static CoverageQueryResource resource(FakeRepository repository, Set<String> scopes) {
        return new CoverageQueryResource(new CoverageQueryService(new FakeAuthenticator(scopes), repository));
    }

    private static final class FakeAuthenticator implements RepositoryApiKeyAuthenticator {
        private final Set<String> scopes;

        private FakeAuthenticator(Set<String> scopes) {
            this.scopes = scopes;
        }

        @Override
        public RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command) {
            return new RepositoryApiKeyPrincipal(REPOSITORY_ID, REPOSITORY_ID, UUID.randomUUID(), scopes, Set.of("*"));
        }
    }

    private static final class FakeRepository implements UploadRepository {
        private Optional<ResolvedCoverageRef> resolved = Optional.of(new ResolvedCoverageRef(
                REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.parse("2026-07-03T00:00:00Z")));
        private CoverageReportDetails report;
        private CoverageFileDetail file;
        private List<String> similarPaths = List.of();
        private Optional<PatchCoverageDetails> patch = Optional.empty();
        private List<dev.vericov.upload.application.CoverageFileSummaryDetails> files = List.of();
        private List<dev.vericov.upload.application.CoverageGapFindingDetails> gaps = List.of();
        private List<dev.vericov.upload.application.CoverageGateEvaluationDetails> gates = List.of();
        private Optional<dev.vericov.upload.application.RepositoryInfo> repositoryInfo = Optional.empty();
        private ResolvedCoverageRef pullRequestResolved;
        private List<dev.vericov.upload.application.CoverageGapManifestEntry> manifestEntries = List.of();

        @Override
        public Optional<dev.vericov.upload.application.QueuedUpload> findById(UUID uploadId) {
            return Optional.empty();
        }

        @Override
        public Optional<dev.vericov.upload.application.QueuedUpload> findByIdempotencyKey(
                UUID repositoryId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void save(dev.vericov.upload.application.QueuedUpload upload,
                List<dev.vericov.upload.application.StoredArtifact> artifacts) {
        }

        @Override
        public List<dev.vericov.upload.application.StoredArtifact> artifactsFor(UUID uploadId) {
            return List.of();
        }

        @Override
        public Optional<CoverageReportDetails> coverageReportFor(UUID uploadId) {
            return Optional.ofNullable(report);
        }

        @Override
        public Optional<ResolvedCoverageRef> resolveCoverageRef(UUID repositoryId, String ref) {
            return resolved;
        }

        @Override
        public Optional<CoverageFileDetail> fileDetail(UUID reportId, String path) {
            return Optional.ofNullable(file);
        }

        @Override
        public List<String> similarFilePaths(UUID reportId, String basename, int max) {
            return similarPaths;
        }

        @Override
        public Optional<PatchCoverageDetails> patchForPullRequest(UUID repositoryId, int pullRequestNumber) {
            return patch;
        }

        @Override
        public List<dev.vericov.upload.application.CoverageFileSummaryDetails> filesFor(
                UUID reportId, String pathPrefix, String componentKey, String sort, int limit, int offset) {
            return files;
        }

        @Override
        public List<dev.vericov.upload.application.CoverageGapFindingDetails> gapsFor(
                UUID reportId, String componentKey, String minRiskLevel, String status, int limit, int offset) {
            return gaps;
        }

        @Override
        public List<dev.vericov.upload.application.CoverageGateEvaluationDetails> gatesFor(UUID reportId) {
            return gates;
        }

        @Override
        public Optional<dev.vericov.upload.application.RepositoryInfo> repositoryInfo(UUID repositoryId) {
            return repositoryInfo;
        }

        @Override
        public Optional<ResolvedCoverageRef> resolveCoverageRefForPullRequest(UUID repositoryId, int pullRequestNumber) {
            return Optional.ofNullable(pullRequestResolved);
        }

        @Override
        public List<dev.vericov.upload.application.CoverageGapManifestEntry> gapManifestEntries(
                UUID reportId, String nextAction, String minRiskLevel, int limit, int offset) {
            return manifestEntries;
        }
    }
}
