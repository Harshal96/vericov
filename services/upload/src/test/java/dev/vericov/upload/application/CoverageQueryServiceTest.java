package dev.vericov.upload.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoverageQueryServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPOSITORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPORT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID UPLOAD_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void requiresReadScope() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:create"));
        CoverageQueryService service = new CoverageQueryService(authenticator, new FakeUploadRepository());

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> service.resolveRef("Bearer vc_repo_test", "main"));

        assertEquals("scope_missing", exception.code());
    }

    @Test
    void resolvesRefThroughTheRepository() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        ResolvedCoverageRef resolved = service.resolveRef("Bearer vc_repo_test", "main");

        assertEquals(REPORT_ID, resolved.reportId());
        assertEquals(REPOSITORY_ID, repository.lastResolvedRepositoryId);
    }

    @Test
    void missingRefThrowsRefNotFound() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        CoverageQueryService service = new CoverageQueryService(authenticator, new FakeUploadRepository());

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> service.resolveRef("Bearer vc_repo_test", "missing-branch"));

        assertEquals("ref_not_found", exception.code());
    }

    @Test
    void fileNotFoundIncludesDidYouMeanSuggestions() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        repository.similarPaths = List.of("src/main/Main.java");
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageQueryService.FileNotFoundQueryException exception = assertThrows(
                CoverageQueryService.FileNotFoundQueryException.class,
                () -> service.file("Bearer vc_repo_test", "main", "Main.java"));

        assertEquals("file_not_found", exception.code());
        assertEquals(List.of("src/main/Main.java"), exception.didYouMean());
    }

    @Test
    void filesPaginationFlagsTruncationAndEncodesCursor() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        repository.fileSummaries = List.of(
                fileSummary("a.java"),
                fileSummary("b.java"),
                fileSummary("c.java"));
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageQueryService.FilePage page = service.files("Bearer vc_repo_test", "main", null, null, null, 2, null);

        assertTrue(page.truncated());
        assertEquals(2, page.files().size());
        assertEquals(0, repository.lastFilesOffset);

        CoverageQueryService.FilePage nextPage =
                service.files("Bearer vc_repo_test", "main", null, null, null, 2, page.nextCursor());
        assertEquals(1, nextPage.files().size());
        assertFalse(nextPage.truncated());
    }

    @Test
    void rejectsInvalidSort() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> service.files("Bearer vc_repo_test", "main", null, null, "nonsense", null, null));

        assertEquals("validation_error", exception.code());
    }

    @Test
    void patchNotFoundThrowsPullRequestNotFound() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        CoverageQueryService service = new CoverageQueryService(authenticator, new FakeUploadRepository());

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> service.patchForPullRequest("Bearer vc_repo_test", 42));

        assertEquals("pull_request_not_found", exception.code());
        assertTrue(exception.getMessage().contains("diff artifact"));
    }

    @Test
    void invalidCursorIsRejected() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        assertFalse(assertThrows(
                InvalidUploadException.class,
                () -> service.files("Bearer vc_repo_test", "main", null, null, null, null, "not-base64!!"))
                .getMessage()
                .isBlank());
    }

    @Test
    void gapsAreReturnedWithTruncationFlag() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        repository.gaps = List.of(gapFinding("a.java", "high"), gapFinding("b.java", "medium"));
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageQueryService.GapPage page =
                service.gaps("Bearer vc_repo_test", "main", null, null, null, 10, null);

        assertEquals(2, page.gaps().size());
        assertFalse(page.truncated());
    }

    @Test
    void gatesAreReturnedForTheResolvedReport() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        repository.gates = List.of(new CoverageGateEvaluationDetails(
                "line-gate", "line_coverage", "line", "repository", null, List.of(),
                new java.math.BigDecimal("80"), new java.math.BigDecimal("75"), "failed", true));
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageQueryService.GateResult result = service.gates("Bearer vc_repo_test", "main");

        assertEquals(1, result.gates().size());
        assertEquals("failed", result.gates().get(0).status());
    }

    @Test
    void componentsAreReturnedForTheResolvedReport() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.report = new CoverageReportDetails(
                UPLOAD_ID, REPOSITORY_ID, "abc123", "main", null, "complete",
                metric, metric, metric, metric, "bucket", "path", Instant.EPOCH);
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageQueryService.ComponentCoverageResult result = service.components("Bearer vc_repo_test", "main");

        assertTrue(result.components().isEmpty());
    }

    @Test
    void manifestAssemblesRepositoryReportAndEntriesForARef() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.report = new CoverageReportDetails(
                UPLOAD_ID, REPOSITORY_ID, "abc123", "main", null, "complete",
                metric, metric, metric, metric, "bucket", "path", Instant.EPOCH);
        repository.repositoryInfo = Optional.of(new RepositoryInfo("acme/payments-api", "main"));
        repository.manifestEntries = List.of(manifestEntry("a.java", "high"));
        repository.gates = List.of(new CoverageGateEvaluationDetails(
                "line-gate", "line_coverage", "line", "repository", null, List.of(),
                new java.math.BigDecimal("80"), new java.math.BigDecimal("75"), "failed", true));
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageGapManifest manifest = service.manifest("Bearer vc_repo_test", "main", null, null, null, 10);

        assertEquals(1, manifest.manifestVersion());
        assertEquals("acme/payments-api", manifest.repository().fullName());
        assertEquals(1, manifest.entries().size());
        assertEquals(1, manifest.failedGates().size());
        assertFalse(manifest.truncated());
    }

    @Test
    void manifestForPullRequestUsesPullRequestResolution() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.pullRequestResolved = new ResolvedCoverageRef(
                REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "feature", Instant.EPOCH);
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.report = new CoverageReportDetails(
                UPLOAD_ID, REPOSITORY_ID, "abc123", "feature", 481, "failed",
                metric, metric, metric, metric, "bucket", "path", Instant.EPOCH);
        repository.repositoryInfo = Optional.of(new RepositoryInfo("acme/payments-api", "main"));
        repository.patch = Optional.of(new PatchCoverageDetails(
                "complete", "base-sha", "head-sha", 4, 5, 0, 1, List.of()));
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageGapManifest manifest = service.manifest("Bearer vc_repo_test", null, 481, null, null, null);

        assertEquals(481, manifest.pullRequestNumber());
        assertEquals("complete", manifest.patch().status());
    }

    @Test
    void manifestForUnknownPullRequestThrowsPullRequestNotFound() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        CoverageQueryService service = new CoverageQueryService(authenticator, new FakeUploadRepository());

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> service.manifest("Bearer vc_repo_test", null, 999, null, null, null));

        assertEquals("pull_request_not_found", exception.code());
    }

    @Test
    void manifestTruncatesWhenMoreEntriesThanLimit() {
        FakeAuthenticator authenticator = new FakeAuthenticator(Set.of("uploads:read"));
        FakeUploadRepository repository = new FakeUploadRepository();
        repository.resolved = new ResolvedCoverageRef(REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.EPOCH);
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        repository.report = new CoverageReportDetails(
                UPLOAD_ID, REPOSITORY_ID, "abc123", "main", null, "complete",
                metric, metric, metric, metric, "bucket", "path", Instant.EPOCH);
        repository.manifestEntries = List.of(manifestEntry("a.java", "high"), manifestEntry("b.java", "high"));
        CoverageQueryService service = new CoverageQueryService(authenticator, repository);

        CoverageGapManifest manifest = service.manifest("Bearer vc_repo_test", "main", null, null, null, 1);

        assertTrue(manifest.truncated());
        assertEquals(1, manifest.entries().size());
    }

    private static CoverageGapManifestEntry manifestEntry(String path, String riskLevel) {
        return new CoverageGapManifestEntry(
                UUID.randomUUID(), 1, path, "line", 3, 4, null, true,
                "uncovered_executable_line", "explanation", "high",
                new java.math.BigDecimal("70.0"), riskLevel, List.of("change_exposure: reason (+25)"),
                "api", List.of("team-api"), "add_test", List.of(new CoverageLineRange(3, 4)), false);
    }

    private static CoverageGapFindingDetails gapFinding(String path, String riskLevel) {
        return new CoverageGapFindingDetails(
                path, "line", 1, 1, null, "uncovered_executable_line", "explanation", "high",
                new java.math.BigDecimal("50.0"), riskLevel, List.of(), null, "add_test", "active");
    }

    private static CoverageFileSummaryDetails fileSummary(String path) {
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        return new CoverageFileSummaryDetails(path, null, List.of(), metric, metric, metric, metric);
    }

    private static final class FakeAuthenticator implements RepositoryApiKeyAuthenticator {
        private final Set<String> scopes;

        private FakeAuthenticator(Set<String> scopes) {
            this.scopes = scopes;
        }

        @Override
        public RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command) {
            return new RepositoryApiKeyPrincipal(TENANT_ID, REPOSITORY_ID, UUID.randomUUID(), scopes, Set.of("*"));
        }
    }

    private static final class FakeUploadRepository extends InMemoryUploadRepository {
        private ResolvedCoverageRef resolved;
        private List<String> similarPaths = List.of();
        private List<CoverageFileSummaryDetails> fileSummaries = List.of();
        private List<CoverageGapFindingDetails> gaps = List.of();
        private List<CoverageGateEvaluationDetails> gates = List.of();
        private CoverageReportDetails report;
        private UUID lastResolvedRepositoryId;
        private int lastFilesOffset;
        private Optional<RepositoryInfo> repositoryInfo = Optional.empty();
        private ResolvedCoverageRef pullRequestResolved;
        private Optional<PatchCoverageDetails> patch = Optional.empty();
        private List<CoverageGapManifestEntry> manifestEntries = List.of();

        @Override
        public Optional<ResolvedCoverageRef> resolveCoverageRef(UUID repositoryId, String ref) {
            lastResolvedRepositoryId = repositoryId;
            return Optional.ofNullable(resolved);
        }

        @Override
        public Optional<RepositoryInfo> repositoryInfo(UUID repositoryId) {
            return repositoryInfo;
        }

        @Override
        public Optional<ResolvedCoverageRef> resolveCoverageRefForPullRequest(UUID repositoryId, int pullRequestNumber) {
            return Optional.ofNullable(pullRequestResolved);
        }

        @Override
        public Optional<PatchCoverageDetails> patchForPullRequest(UUID repositoryId, int pullRequestNumber) {
            return patch;
        }

        @Override
        public List<CoverageGapManifestEntry> gapManifestEntries(
                UUID reportId, String nextAction, String minRiskLevel, int limit, int offset) {
            int end = Math.min(offset + limit, manifestEntries.size());
            return offset >= manifestEntries.size() ? List.of() : manifestEntries.subList(offset, end);
        }

        @Override
        public Optional<CoverageReportDetails> coverageReportFor(UUID uploadId) {
            return Optional.ofNullable(report);
        }

        @Override
        public Optional<CoverageFileDetail> fileDetail(UUID reportId, String path) {
            return Optional.empty();
        }

        @Override
        public List<String> similarFilePaths(UUID reportId, String basename, int max) {
            return similarPaths;
        }

        @Override
        public List<CoverageFileSummaryDetails> filesFor(
                UUID reportId, String pathPrefix, String componentKey, String sort, int limit, int offset) {
            lastFilesOffset = offset;
            int end = Math.min(offset + limit, fileSummaries.size());
            if (offset >= fileSummaries.size()) {
                return List.of();
            }
            return fileSummaries.subList(offset, end);
        }

        @Override
        public List<CoverageGapFindingDetails> gapsFor(
                UUID reportId, String componentKey, String minRiskLevel, String status, int limit, int offset) {
            int end = Math.min(offset + limit, gaps.size());
            return offset >= gaps.size() ? List.of() : gaps.subList(offset, end);
        }

        @Override
        public List<CoverageGateEvaluationDetails> gatesFor(UUID reportId) {
            return gates;
        }
    }
}
