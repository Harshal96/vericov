package dev.vericov.upload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.CoverageMetricDetails;
import dev.vericov.upload.application.DashboardComponentRollup;
import dev.vericov.upload.application.DashboardFileLineHit;
import dev.vericov.upload.application.DashboardFileSummary;
import dev.vericov.upload.application.DashboardGapCounts;
import dev.vericov.upload.application.DashboardGapFinding;
import dev.vericov.upload.application.DashboardGateConfig;
import dev.vericov.upload.application.DashboardGateEvaluation;
import dev.vericov.upload.application.DashboardPullRequestDiff;
import dev.vericov.upload.application.DashboardPullRequestDiffDetails;
import dev.vericov.upload.application.DashboardPullRequestDiffFile;
import dev.vericov.upload.application.DashboardReport;
import dev.vericov.upload.application.DashboardReportDetails;
import dev.vericov.upload.application.DashboardReportListItem;
import dev.vericov.upload.application.DashboardRepository;
import dev.vericov.upload.application.DashboardRepositoryOverview;
import dev.vericov.upload.application.DashboardQueryService;
import dev.vericov.upload.application.DashboardTestRun;
import dev.vericov.upload.application.DashboardTrendPoint;
import dev.vericov.upload.application.DashboardUploadArtifact;
import dev.vericov.upload.application.DashboardUploadDetails;
import dev.vericov.upload.application.DashboardUploadEvent;
import dev.vericov.upload.application.DashboardUploadListItem;
import dev.vericov.upload.application.CoverageGapManifestEntry;
import dev.vericov.upload.application.CoverageGateEvaluationDetails;
import dev.vericov.upload.application.CoverageLineRange;
import dev.vericov.upload.application.CoverageReportDetails;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.PatchCoverageDetails;
import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.RepositoryInfo;
import dev.vericov.upload.application.ResolvedCoverageRef;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.port.DashboardQueryRepository;
import dev.vericov.upload.application.port.TenantAuthenticator;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.domain.TenantPrincipal;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardQueryResourceTest {
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID REPOSITORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final UUID UPLOAD_ID = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final UUID PR_DIFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000080");

    @Test
    void overviewReturnsEnvelopeWithSnakeCasePayload() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new OverviewRepository(new DashboardOverview(8, 6, new BigDecimal("81.40"), 412, 37, 3, 5)));

        Response response = resource.overview("Bearer internal-token");

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardOverviewHttpResponse>) response.getEntity();
        assertEquals(8, body.data().repoCount());
        assertEquals(new BigDecimal("81.40"), body.data().weightedLineCoverage());
    }

    @Test
    void overviewReturns401WhenTokenIsMissing() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> {
                    throw new InvalidUploadException("unauthorized", "Invalid internal token");
                },
                new OverviewRepository(new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.overview(null);

        assertEquals(401, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("unauthorized", error.error().code());
    }

    @Test
    void overviewReturns403WhenOrgHasNoTenantMapping() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> {
                    throw new InvalidUploadException("tenant_not_provisioned", "No tenant");
                },
                new OverviewRepository(new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.overview("Bearer internal-token");

        assertEquals(403, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("tenant_not_provisioned", error.error().code());
    }

    @Test
    void serviceQueriesWithAuthenticatedTenantId() {
        RecordingRepository repository = new RecordingRepository();
        DashboardQueryService service = new DashboardQueryService(
                authorizationHeader -> principal(), repository, new NoOpUploadRepository());

        service.overview("Bearer internal-token");
        service.repositories("Bearer internal-token");
        service.sparklines("Bearer internal-token", 99);
        service.repository("Bearer internal-token", REPOSITORY_ID);
        service.trend("Bearer internal-token", REPOSITORY_ID, " ", 300);
        service.reports("Bearer internal-token", REPOSITORY_ID, 300);
        service.report("Bearer internal-token", REPORT_ID);
        service.reportFiles("Bearer internal-token", REPORT_ID);
        service.reportLineHits("Bearer internal-token", REPORT_ID, "src/App.java");
        service.reportComponents("Bearer internal-token", REPORT_ID);
        service.gaps("Bearer internal-token", "active", 999);
        service.repositoryGaps("Bearer internal-token", REPOSITORY_ID, " ", null);
        service.repositoryGapCounts("Bearer internal-token", REPOSITORY_ID);
        service.gateConfigs("Bearer internal-token", REPOSITORY_ID);
        service.repositoryGateEvaluations("Bearer internal-token", REPOSITORY_ID, 999);
        service.reportGates("Bearer internal-token", REPORT_ID);
        service.pullRequestDiffs("Bearer internal-token", REPOSITORY_ID, 999);
        service.pullRequestDiff("Bearer internal-token", PR_DIFF_ID);

        assertEquals(TENANT_ID, repository.tenantId);
        assertEquals(60, repository.perRepository);
        assertEquals(200, repository.trendLimit);
        assertEquals(100, repository.reportLimit);
        assertEquals(500, repository.gapLimit);
        assertEquals(200, repository.repositoryGapLimit);
        assertEquals(200, repository.repositoryGateEvaluationLimit);
        assertEquals(null, repository.repositoryGapStatus);
        assertEquals(REPOSITORY_ID, repository.repositoryId);
        assertEquals(REPORT_ID, repository.reportId);
        assertEquals("src/App.java", repository.filePath);
        assertEquals(REPOSITORY_ID, repository.pullRequestDiffRepositoryId);
        assertEquals(100, repository.pullRequestDiffLimit);
        assertEquals(PR_DIFF_ID, repository.pullRequestDiffId);
    }

    @Test
    void repositoriesReturnsEnvelopeWithLatestReportPayload() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new DashboardQueryRepository() {
                    @Override
                    public DashboardOverview overview(UUID tenantId) {
                        return new DashboardOverview(0, 0, null, 0, 0, 0, 0);
                    }

                    @Override
                    public List<DashboardRepositoryOverview> repositories(UUID tenantId) {
                        return List.of(new DashboardRepositoryOverview(
                                REPOSITORY_ID,
                                "acme/checkout",
                                "github",
                                "main",
                                "private",
                                "active",
                                Instant.parse("2026-07-04T18:00:00Z"),
                                UUID.fromString("00000000-0000-0000-0000-000000000030"),
                                "abc123",
                                Instant.parse("2026-07-04T19:00:00Z"),
                                812,
                                1000,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                new BigDecimal("1.20"),
                                57,
                                4,
                                1));
                    }

                    @Override
                    public Map<UUID, List<BigDecimal>> sparklines(UUID tenantId, int perRepository) {
                        return Map.of();
                    }

                    @Override
                    public Optional<DashboardRepository> repository(UUID tenantId, UUID repositoryId) {
                        return Optional.empty();
                    }

                    @Override
                    public List<DashboardTrendPoint> trend(
                            UUID tenantId, UUID repositoryId, String branch, int limit) {
                        return List.of();
                    }

                    @Override
                    public List<DashboardReportListItem> reports(UUID tenantId, UUID repositoryId, int limit) {
                        return List.of();
                    }

                    @Override
                    public Optional<DashboardReportDetails> report(UUID tenantId, UUID reportId) {
                        return Optional.empty();
                    }
                });

        Response response = resource.repositories("Bearer internal-token");

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardRepositoryListHttpResponse>) response.getEntity();
        assertEquals("acme/checkout", body.data().repos().getFirst().fullName());
        assertEquals(new BigDecimal("1.20"), body.data().repos().getFirst().lineDelta());
    }

    @Test
    void sparklinesReturnsStringKeyedMap() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.sparklines("Bearer internal-token", 20);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardSparklinesHttpResponse>) response.getEntity();
        assertEquals(List.of(new BigDecimal("78.10"), new BigDecimal("79.00")),
                body.data().sparklines().get(REPOSITORY_ID.toString()));
    }

    @Test
    void repositoryReturns404WhenTenantScopedLookupMisses() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new DashboardQueryRepository() {
            @Override
            public DashboardOverview overview(UUID tenantId) {
                return new DashboardOverview(0, 0, null, 0, 0, 0, 0);
            }

            @Override
            public List<DashboardRepositoryOverview> repositories(UUID tenantId) {
                return List.of();
            }

            @Override
            public Map<UUID, List<BigDecimal>> sparklines(UUID tenantId, int perRepository) {
                return Map.of();
            }

            @Override
            public Optional<DashboardRepository> repository(UUID tenantId, UUID repositoryId) {
                return Optional.empty();
            }

            @Override
            public List<DashboardTrendPoint> trend(UUID tenantId, UUID repositoryId, String branch, int limit) {
                return List.of();
            }

            @Override
            public List<DashboardReportListItem> reports(UUID tenantId, UUID repositoryId, int limit) {
                return List.of();
            }

            @Override
            public Optional<DashboardReportDetails> report(UUID tenantId, UUID reportId) {
                return Optional.empty();
            }
        });

        Response response = resource.repository("Bearer internal-token", REPOSITORY_ID);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("repo_not_found", error.error().code());
    }

    @Test
    void trendReturnsPointsEnvelope() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.trend("Bearer internal-token", REPOSITORY_ID, null, null);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardTrendHttpResponse>) response.getEntity();
        assertEquals(REPORT_ID, body.data().points().getFirst().reportId());
        assertEquals(new BigDecimal("81.20"), body.data().points().getFirst().linePct());
    }

    @Test
    void reportsReturnsCiMetadataAndFlags() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.reports("Bearer internal-token", REPOSITORY_ID, 30);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardReportListHttpResponse>) response.getEntity();
        assertEquals("github_actions", body.data().reports().getFirst().ciProvider());
        assertEquals(List.of("unit"), body.data().reports().getFirst().flags());
    }

    @Test
    void reportReturns404WhenTenantScopedLookupMisses() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new OverviewRepository(
                new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.report("Bearer internal-token", REPORT_ID);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("report_not_found", error.error().code());
    }

    @Test
    void reportFilesReturnsDashboardFileShape() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.reportFiles("Bearer internal-token", REPORT_ID);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardFileSummaryListHttpResponse>) response.getEntity();
        DashboardFileSummaryHttpResponse file = body.data().files().getFirst();
        assertEquals("src/App.java", file.filePath());
        assertEquals("checkout", file.packageName());
        assertEquals(10, file.lineCovered());
        assertEquals("", body.data().nextCursor());
    }

    @Test
    void reportLineHitsReturns400WhenFilePathIsMissing() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.reportLineHits("Bearer internal-token", REPORT_ID, " ");

        assertEquals(400, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("missing_file_path", error.error().code());
    }

    @Test
    void reportLineHitsReturnsDidYouMeanDetailsForUnknownFile() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository(false));

        Response response = resource.reportLineHits("Bearer internal-token", REPORT_ID, "App.java");

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("file_not_found", error.error().code());
        assertEquals("path", error.error().details().getFirst().field());
        assertEquals("did_you_mean", error.error().details().getFirst().code());
        assertEquals("src/App.java", error.error().details().getFirst().message());
    }

    @Test
    void reportLineHitsReturnsRawHitCounts() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.reportLineHits("Bearer internal-token", REPORT_ID, "src/App.java");

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardFileLineHitListHttpResponse>) response.getEntity();
        assertEquals(12, body.data().lines().getFirst().lineNumber());
        assertEquals(0, body.data().lines().getFirst().hits());
    }

    @Test
    void reportComponentsReturnsFlatRollupShape() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.reportComponents("Bearer internal-token", REPORT_ID);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardComponentRollupListHttpResponse>) response.getEntity();
        DashboardComponentRollupHttpResponse component = body.data().components().getFirst();
        assertEquals("commerce/payments-api", component.componentId());
        assertEquals("team-payments", component.owner());
        assertEquals(new BigDecimal("27.50"), component.riskScoreTotal());
    }

    @Test
    void gapsReturnsTenantWideLatestDefaultBranchGapShape() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.gaps("Bearer internal-token", "active", null);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardGapListHttpResponse>) response.getEntity();
        DashboardGapFindingHttpResponse gap = body.data().gaps().getFirst();
        assertEquals("00000000-0000-0000-0000-000000000050", gap.id().toString());
        assertEquals(REPOSITORY_ID, gap.repositoryId());
        assertEquals(REPORT_ID, gap.coverageReportId());
        assertEquals("src/App.java", gap.filePath());
        assertEquals(new BigDecimal("8.5"), gap.riskScore());
        assertEquals("abc123", gap.commitSha());
        assertEquals(null, gap.pullRequestNumber());
    }

    @Test
    void repositoryGapsUseRepositoryDefaultLimitAndBlankStatusAsAll() {
        RecordingRepository repository = new RecordingRepository();
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), repository);

        Response response = resource.repositoryGaps("Bearer internal-token", REPOSITORY_ID, " ", null);

        assertEquals(200, response.getStatus());
        assertEquals(200, repository.repositoryGapLimit);
        assertEquals(null, repository.repositoryGapStatus);
    }

    @Test
    void repositoryGapCountsReturnRiskLevelBuckets() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.repositoryGapCounts("Bearer internal-token", REPOSITORY_ID);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardGapCountsHttpResponse>) response.getEntity();
        assertEquals(1, body.data().critical());
        assertEquals(4, body.data().high());
        assertEquals(7, body.data().medium());
        assertEquals(2, body.data().low());
    }

    @Test
    void repositoryGapManifestMatchesAgentManifestShape() {
        CoverageMetricDetails metric = new CoverageMetricDetails(1, 2);
        FakeUploadRepository uploadRepository = new FakeUploadRepository();
        uploadRepository.report = new CoverageReportDetails(
                UPLOAD_ID, REPOSITORY_ID, "abc123", "main", null, "complete",
                metric, metric, metric, metric, "bucket", "path", "sha256", "failed",
                List.of(), List.of(), null, Instant.parse("2026-07-03T00:00:00Z"));
        uploadRepository.repositoryInfo = Optional.of(new RepositoryInfo("acme/api", "main"));
        uploadRepository.gates = List.of(new CoverageGateEvaluationDetails(
                "line-gate", "line_coverage", "line", "repository", null, List.of(),
                new BigDecimal("80"), new BigDecimal("75"), "failed", true));
        uploadRepository.manifestEntries = List.of(new CoverageGapManifestEntry(
                UUID.randomUUID(), 1, "src/Retry.java", "range", 84, 97, null, true,
                "new_uncovered_changed_line", "explanation", "high",
                new BigDecimal("78.0"), "high", List.of("change_exposure: reason (+25)"),
                "payments-api", List.of("team-payments"), "add_test",
                List.of(new CoverageLineRange(84, 91), new CoverageLineRange(95, 97)), false));
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(), new RecordingRepository(), uploadRepository);

        Response response = resource.repositoryGapManifest(
                "Bearer internal-token", REPOSITORY_ID, "main", null, null, null, null);

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
    void repositoryGapManifestReturns404WhenRepositoryNotFound() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new OverviewRepository(new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.repositoryGapManifest(
                "Bearer internal-token", REPOSITORY_ID, "main", null, null, null, null);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("repo_not_found", error.error().code());
    }

    @Test
    void repositoryGapManifestForUnknownPullRequestReturns404() {
        FakeUploadRepository uploadRepository = new FakeUploadRepository();
        uploadRepository.pullRequestResolved = Optional.empty();
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(), new RecordingRepository(), uploadRepository);

        Response response = resource.repositoryGapManifest(
                "Bearer internal-token", REPOSITORY_ID, null, 999, null, null, null);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("pull_request_not_found", error.error().code());
    }

    @Test
    void gateConfigsReturnConfiguredPoliciesIncludingInactiveRows() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.gateConfigs("Bearer internal-token", REPOSITORY_ID);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardGateConfigListHttpResponse>) response.getEntity();
        DashboardGateConfigHttpResponse config = body.data().configs().getFirst();
        assertEquals("project line floor", config.name());
        assertEquals("project_coverage", config.gateType());
        assertEquals("line", config.metric());
        assertEquals(new BigDecimal("80.0"), config.threshold());
        assertEquals(null, config.maxDrop());
        assertEquals(true, config.blocking());
        assertEquals("disabled", config.status());
    }

    @Test
    void repositoryGateEvaluationsUseDefaultLimitAndReturnHistoryShape() {
        RecordingRepository repository = new RecordingRepository();
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), repository);

        Response response = resource.repositoryGateEvaluations("Bearer internal-token", REPOSITORY_ID, null);

        assertEquals(200, response.getStatus());
        assertEquals(60, repository.repositoryGateEvaluationLimit);
        var body = (ApiResponse<DashboardGateEvaluationListHttpResponse>) response.getEntity();
        DashboardGateEvaluationHttpResponse evaluation = body.data().evaluations().getFirst();
        assertEquals("project line floor", evaluation.gateName());
        assertEquals("failed", evaluation.status());
        assertEquals("abc123", evaluation.commitSha());
        assertEquals("main", evaluation.branch());
        assertEquals(REPORT_ID, evaluation.coverageReportId());
    }

    @Test
    void reportGatesReturnGatesTopLevelKey() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                new RecordingRepository());

        Response response = resource.reportGates("Bearer internal-token", REPORT_ID);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardReportGateListHttpResponse>) response.getEntity();
        DashboardGateEvaluationHttpResponse evaluation = body.data().gates().getFirst();
        assertEquals("project line floor", evaluation.gateName());
        assertEquals(new BigDecimal("78.9"), evaluation.actual());
        assertEquals(Instant.parse("2026-07-04T20:00:00Z"), evaluation.evaluatedAt());
    }

    @Test
    void pullRequestDiffsReturnsLatestDiffPerPullRequest() {
        RecordingRepository repository = new RecordingRepository();
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), repository);

        Response response = resource.pullRequestDiffs("Bearer internal-token", REPOSITORY_ID, 40);

        assertEquals(200, response.getStatus());
        assertEquals(REPOSITORY_ID, repository.pullRequestDiffRepositoryId);
        assertEquals(40, repository.pullRequestDiffLimit);
        var body = (ApiResponse<DashboardPullRequestDiffListHttpResponse>) response.getEntity();
        DashboardPullRequestDiffHttpResponse diff = body.data().diffs().getFirst();
        assertEquals(481, diff.pullRequestNumber());
        assertEquals(34, diff.patchLineCovered());
        assertEquals(40, diff.patchLineTotal());
        assertEquals(new BigDecimal("81.20"), diff.projectLinePct());
    }

    @Test
    void pullRequestDiffsReturns404WhenRepositoryMissing() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new OverviewRepository(
                new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.pullRequestDiffs("Bearer internal-token", REPOSITORY_ID, null);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("repo_not_found", error.error().code());
    }

    @Test
    void pullRequestDiffReturnsDetailWithNestedFiles() {
        RecordingRepository repository = new RecordingRepository();
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), repository);

        Response response = resource.pullRequestDiff("Bearer internal-token", PR_DIFF_ID);

        assertEquals(200, response.getStatus());
        assertEquals(PR_DIFF_ID, repository.pullRequestDiffId);
        var body = (ApiResponse<DashboardPullRequestDiffDetailHttpResponse>) response.getEntity();
        assertEquals(481, body.data().pullRequestNumber());
        assertEquals(1, body.data().files().size());
        assertEquals("src/App.java", body.data().files().getFirst().filePath());
        assertEquals("modified", body.data().files().getFirst().changeStatus());
    }

    @Test
    void pullRequestDiffReturns404WhenDiffMissing() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new OverviewRepository(
                new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.pullRequestDiff("Bearer internal-token", PR_DIFF_ID);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("pull_request_not_found", error.error().code());
    }

    @Test
    void testRunsReturnsSuiteHistoryForRepository() {
        RecordingRepository repository = new RecordingRepository();
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), repository);

        Response response = resource.testRuns("Bearer internal-token", REPOSITORY_ID, 60);

        assertEquals(200, response.getStatus());
        assertEquals(REPOSITORY_ID, repository.testRunRepositoryId);
        assertEquals(60, repository.testRunLimit);
        var body = (ApiResponse<DashboardTestRunListHttpResponse>) response.getEntity();
        DashboardTestRunHttpResponse run = body.data().testRuns().getFirst();
        assertEquals("unit", run.suiteName());
        assertEquals("passed", run.status());
        assertEquals(412, run.totalCount());
        assertEquals(410, run.passedCount());
    }

    @Test
    void testRunsReturns404WhenRepositoryMissing() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new OverviewRepository(
                new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.testRuns("Bearer internal-token", REPOSITORY_ID, null);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("repo_not_found", error.error().code());
    }

    @Test
    void uploadsReturnsTenantScopedListingWithDefaultLimit() {
        RecordingRepository repository = new RecordingRepository();
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), repository);

        Response response = resource.uploads("Bearer internal-token", REPOSITORY_ID, "processed", null);

        assertEquals(200, response.getStatus());
        assertEquals(TENANT_ID, repository.tenantId);
        assertEquals(REPOSITORY_ID, repository.uploadRepositoryId);
        assertEquals("processed", repository.uploadStatus);
        assertEquals(50, repository.uploadLimit);
        var body = (ApiResponse<DashboardUploadListHttpResponse>) response.getEntity();
        DashboardUploadListItemHttpResponse upload = body.data().uploads().getFirst();
        assertEquals(UPLOAD_ID, upload.id());
        assertEquals("processed", upload.status());
        assertEquals(REPORT_ID, upload.coverageReportId());
    }

    @Test
    void uploadReturnsDetailsWithLifecycleEvents() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new RecordingRepository());

        Response response = resource.upload("Bearer internal-token", UPLOAD_ID);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardUploadDetailsHttpResponse>) response.getEntity();
        assertEquals(UPLOAD_ID, body.data().upload().id());
        assertEquals(1, body.data().events().size());
        assertEquals("upload.received", body.data().events().getFirst().eventType());
    }

    @Test
    void uploadReturns404WhenUploadMissing() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new OverviewRepository(
                new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.upload("Bearer internal-token", UPLOAD_ID);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("upload_not_found", error.error().code());
    }

    @Test
    void uploadArtifactsReturnsArtifactMetadata() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new RecordingRepository());

        Response response = resource.uploadArtifacts("Bearer internal-token", UPLOAD_ID);

        assertEquals(200, response.getStatus());
        var body = (ApiResponse<DashboardUploadArtifactListHttpResponse>) response.getEntity();
        DashboardUploadArtifactHttpResponse artifact = body.data().artifacts().getFirst();
        assertEquals("coverage.xml", artifact.name());
        assertEquals("coverage", artifact.kind());
    }

    @Test
    void uploadArtifactsReturns404WhenUploadMissing() {
        DashboardQueryResource resource = resource(authorizationHeader -> principal(), new OverviewRepository(
                new DashboardOverview(0, 0, null, 0, 0, 0, 0)));

        Response response = resource.uploadArtifacts("Bearer internal-token", UPLOAD_ID);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("upload_not_found", error.error().code());
    }

    private static DashboardQueryResource resource(
            TenantAuthenticator authenticator,
            DashboardQueryRepository repository) {
        return resource(authenticator, repository, new NoOpUploadRepository());
    }

    private static DashboardQueryResource resource(
            TenantAuthenticator authenticator,
            DashboardQueryRepository repository,
            UploadRepository uploadRepository) {
        return new DashboardQueryResource(new DashboardQueryService(authenticator, repository, uploadRepository));
    }

    private static class NoOpUploadRepository implements UploadRepository {
        @Override
        public Optional<QueuedUpload> findById(UUID uploadId) {
            return Optional.empty();
        }

        @Override
        public Optional<QueuedUpload> findByIdempotencyKey(UUID repositoryId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void save(QueuedUpload upload, List<StoredArtifact> artifacts) {
        }

        @Override
        public List<StoredArtifact> artifactsFor(UUID uploadId) {
            return List.of();
        }
    }

    private static final class FakeUploadRepository extends NoOpUploadRepository {
        private Optional<ResolvedCoverageRef> resolved = Optional.of(new ResolvedCoverageRef(
                REPORT_ID, UPLOAD_ID, REPOSITORY_ID, "abc123", "main", Instant.parse("2026-07-03T00:00:00Z")));
        private Optional<ResolvedCoverageRef> pullRequestResolved = resolved;
        private CoverageReportDetails report;
        private Optional<RepositoryInfo> repositoryInfo = Optional.empty();
        private Optional<PatchCoverageDetails> patch = Optional.empty();
        private List<CoverageGateEvaluationDetails> gates = List.of();
        private List<CoverageGapManifestEntry> manifestEntries = List.of();

        @Override
        public Optional<CoverageReportDetails> coverageReportFor(UUID uploadId) {
            return Optional.ofNullable(report);
        }

        @Override
        public Optional<ResolvedCoverageRef> resolveCoverageRef(UUID repositoryId, String ref) {
            return resolved;
        }

        @Override
        public Optional<ResolvedCoverageRef> resolveCoverageRefForPullRequest(
                UUID repositoryId, int pullRequestNumber) {
            return pullRequestResolved;
        }

        @Override
        public Optional<RepositoryInfo> repositoryInfo(UUID repositoryId) {
            return repositoryInfo;
        }

        @Override
        public Optional<PatchCoverageDetails> patchForPullRequest(UUID repositoryId, int pullRequestNumber) {
            return patch;
        }

        @Override
        public List<CoverageGateEvaluationDetails> gatesFor(UUID reportId) {
            return gates;
        }

        @Override
        public List<CoverageGapManifestEntry> gapManifestEntries(
                UUID reportId, String nextAction, String minRiskLevel, int limit, int offset) {
            return manifestEntries;
        }
    }

    private static TenantPrincipal principal() {
        return new TenantPrincipal(TENANT_ID, ORG_ID, "user-1", Set.of("member"), Set.of("cov:read"));
    }

    private static final class OverviewRepository implements DashboardQueryRepository {
        private final DashboardOverview overview;

        private OverviewRepository(DashboardOverview overview) {
            this.overview = overview;
        }

        @Override
        public DashboardOverview overview(UUID tenantId) {
            return overview;
        }

        @Override
        public List<DashboardRepositoryOverview> repositories(UUID tenantId) {
            return List.of();
        }

        @Override
        public Map<UUID, List<BigDecimal>> sparklines(UUID tenantId, int perRepository) {
            return Map.of();
        }

        @Override
        public Optional<DashboardRepository> repository(UUID tenantId, UUID repositoryId) {
            return Optional.empty();
        }

        @Override
        public List<DashboardTrendPoint> trend(UUID tenantId, UUID repositoryId, String branch, int limit) {
            return List.of();
        }

        @Override
        public List<DashboardReportListItem> reports(UUID tenantId, UUID repositoryId, int limit) {
            return List.of();
        }

        @Override
        public Optional<DashboardReportDetails> report(UUID tenantId, UUID reportId) {
            return Optional.empty();
        }
    }

    private static final class RecordingRepository implements DashboardQueryRepository {
        private UUID tenantId;
        private UUID repositoryId;
        private UUID reportId;
        private String filePath;
        private String repositoryGapStatus;
        private int perRepository;
        private int trendLimit;
        private int reportLimit;
        private int gapLimit;
        private int repositoryGapLimit;
        private int repositoryGateEvaluationLimit;
        private UUID pullRequestDiffRepositoryId;
        private int pullRequestDiffLimit;
        private UUID pullRequestDiffId;
        private UUID testRunRepositoryId;
        private int testRunLimit;
        private UUID uploadRepositoryId;
        private String uploadStatus;
        private int uploadLimit;
        private final boolean fileExists;

        private RecordingRepository() {
            this(true);
        }

        private RecordingRepository(boolean fileExists) {
            this.fileExists = fileExists;
        }

        @Override
        public DashboardOverview overview(UUID tenantId) {
            this.tenantId = tenantId;
            return new DashboardOverview(0, 0, null, 0, 0, 0, 0);
        }

        @Override
        public List<DashboardRepositoryOverview> repositories(UUID tenantId) {
            this.tenantId = tenantId;
            return List.of();
        }

        @Override
        public Map<UUID, List<BigDecimal>> sparklines(UUID tenantId, int perRepository) {
            this.tenantId = tenantId;
            this.perRepository = perRepository;
            return Map.of(REPOSITORY_ID, List.of(new BigDecimal("78.10"), new BigDecimal("79.00")));
        }

        @Override
        public Optional<DashboardRepository> repository(UUID tenantId, UUID repositoryId) {
            this.tenantId = tenantId;
            this.repositoryId = repositoryId;
            return Optional.of(new DashboardRepository(
                    repositoryId,
                    "acme/checkout",
                    "github",
                    "main",
                    "private",
                    "active",
                    Instant.parse("2026-07-04T18:00:00Z")));
        }

        @Override
        public List<DashboardTrendPoint> trend(UUID tenantId, UUID repositoryId, String branch, int limit) {
            this.tenantId = tenantId;
            this.repositoryId = repositoryId;
            this.trendLimit = limit;
            return List.of(new DashboardTrendPoint(
                    REPORT_ID,
                    "abc123",
                    Instant.parse("2026-07-04T19:00:00Z"),
                    new BigDecimal("81.20"),
                    null,
                    new BigDecimal("74.00"),
                    null));
        }

        @Override
        public List<DashboardReportListItem> reports(UUID tenantId, UUID repositoryId, int limit) {
            this.tenantId = tenantId;
            this.repositoryId = repositoryId;
            this.reportLimit = limit;
            return List.of(new DashboardReportListItem(
                    report(),
                    new BigDecimal("-0.40"),
                    "github_actions",
                    "https://ci.example/build-1",
                    List.of("unit")));
        }

        @Override
        public Optional<DashboardReportDetails> report(UUID tenantId, UUID reportId) {
            this.tenantId = tenantId;
            this.reportId = reportId;
            return Optional.of(new DashboardReportDetails(
                    report(),
                    new DashboardRepository(
                            REPOSITORY_ID,
                            "acme/checkout",
                            "github",
                            "main",
                            "private",
                            "active",
                            Instant.parse("2026-07-04T18:00:00Z"))));
        }

        @Override
        public List<DashboardFileSummary> reportFiles(UUID tenantId, UUID reportId) {
            this.tenantId = tenantId;
            this.reportId = reportId;
            return List.of(new DashboardFileSummary(
                    "src/App.java",
                    "checkout",
                    List.of("team-payments"),
                    new CoverageMetricDetails(10, 40),
                    new CoverageMetricDetails(2, 8),
                    new CoverageMetricDetails(1, 2),
                    new CoverageMetricDetails(12, 45)));
        }

        @Override
        public boolean reportFileExists(UUID tenantId, UUID reportId, String filePath) {
            this.tenantId = tenantId;
            this.reportId = reportId;
            this.filePath = filePath;
            return fileExists;
        }

        @Override
        public List<DashboardFileLineHit> reportLineHits(UUID tenantId, UUID reportId, String filePath) {
            this.tenantId = tenantId;
            this.reportId = reportId;
            this.filePath = filePath;
            return List.of(new DashboardFileLineHit(12, 0), new DashboardFileLineHit(13, 4));
        }

        @Override
        public List<String> similarReportFilePaths(UUID tenantId, UUID reportId, String basename, int max) {
            this.tenantId = tenantId;
            this.reportId = reportId;
            return List.of("src/" + basename);
        }

        @Override
        public List<DashboardComponentRollup> reportComponents(UUID tenantId, UUID reportId) {
            this.tenantId = tenantId;
            this.reportId = reportId;
            return List.of(new DashboardComponentRollup(
                    "commerce/payments-api",
                    "team-payments",
                    new CoverageMetricDetails(512, 640),
                    new CoverageMetricDetails(50, 80),
                    new CoverageMetricDetails(20, 25),
                    new CoverageMetricDetails(600, 720),
                    3,
                    1,
                    new BigDecimal("27.50"),
                    "high"));
        }

        @Override
        public List<DashboardGapFinding> gaps(UUID tenantId, String status, int limit) {
            this.tenantId = tenantId;
            this.repositoryGapStatus = status;
            this.gapLimit = limit;
            return List.of(gap());
        }

        @Override
        public List<DashboardGapFinding> repositoryGaps(UUID tenantId, UUID repositoryId, String status, int limit) {
            this.tenantId = tenantId;
            this.repositoryId = repositoryId;
            this.repositoryGapStatus = status;
            this.repositoryGapLimit = limit;
            return List.of(gap());
        }

        @Override
        public DashboardGapCounts repositoryGapCounts(UUID tenantId, UUID repositoryId) {
            this.tenantId = tenantId;
            this.repositoryId = repositoryId;
            return new DashboardGapCounts(1, 4, 7, 2);
        }

        @Override
        public List<DashboardGateConfig> gateConfigs(UUID tenantId, UUID repositoryId) {
            this.tenantId = tenantId;
            this.repositoryId = repositoryId;
            return List.of(new DashboardGateConfig(
                    UUID.fromString("00000000-0000-0000-0000-000000000060"),
                    "project line floor",
                    "project_coverage",
                    "line",
                    new BigDecimal("80.0"),
                    null,
                    true,
                    "disabled"));
        }

        @Override
        public List<DashboardGateEvaluation> repositoryGateEvaluations(
                UUID tenantId, UUID repositoryId, int limit) {
            this.tenantId = tenantId;
            this.repositoryId = repositoryId;
            this.repositoryGateEvaluationLimit = limit;
            return List.of(gateEvaluation());
        }

        @Override
        public List<DashboardGateEvaluation> reportGates(UUID tenantId, UUID reportId) {
            this.tenantId = tenantId;
            this.reportId = reportId;
            return List.of(gateEvaluation());
        }

        @Override
        public List<DashboardPullRequestDiff> pullRequestDiffs(UUID tenantId, UUID repositoryId, int limit) {
            this.tenantId = tenantId;
            this.pullRequestDiffRepositoryId = repositoryId;
            this.pullRequestDiffLimit = limit;
            return List.of(pullRequestDiff());
        }

        @Override
        public Optional<DashboardPullRequestDiffDetails> pullRequestDiff(UUID tenantId, UUID diffId) {
            this.tenantId = tenantId;
            this.pullRequestDiffId = diffId;
            return Optional.of(new DashboardPullRequestDiffDetails(
                    pullRequestDiff(), List.of(pullRequestDiffFile())));
        }

        @Override
        public List<DashboardTestRun> testRuns(UUID tenantId, UUID repositoryId, int limit) {
            this.tenantId = tenantId;
            this.testRunRepositoryId = repositoryId;
            this.testRunLimit = limit;
            return List.of(testRun());
        }

        private static DashboardReport report() {
            return new DashboardReport(
                    REPORT_ID,
                    UPLOAD_ID,
                    REPOSITORY_ID,
                    "abc123",
                    "main",
                    null,
                    "complete",
                    Instant.parse("2026-07-04T19:00:00Z"),
                    812,
                    1000,
                    0,
                    0,
                    74,
                    100,
                    0,
                    0);
        }

        private static DashboardGapFinding gap() {
            return new DashboardGapFinding(
                    UUID.fromString("00000000-0000-0000-0000-000000000050"),
                    REPORT_ID,
                    REPOSITORY_ID,
                    "src/App.java",
                    "function",
                    12,
                    34,
                    "retryPayment",
                    "uncovered_branch",
                    "No test exercises the retry branch.",
                    "high",
                    new BigDecimal("8.5"),
                    "critical",
                    List.of("team-payments"),
                    "write_test",
                    "active",
                    "abc123",
                    null);
        }

        private static DashboardGateEvaluation gateEvaluation() {
            return new DashboardGateEvaluation(
                    UUID.fromString("00000000-0000-0000-0000-000000000061"),
                    "project line floor",
                    "project_coverage",
                    "line",
                    new BigDecimal("80.0"),
                    new BigDecimal("78.9"),
                    "failed",
                    true,
                    "abc123",
                    "main",
                    null,
                    Instant.parse("2026-07-04T20:00:00Z"),
                    REPORT_ID);
        }

        private static DashboardPullRequestDiff pullRequestDiff() {
            return new DashboardPullRequestDiff(
                    PR_DIFF_ID,
                    481,
                    "base123",
                    "head456",
                    "complete",
                    new CoverageMetricDetails(34, 40),
                    6,
                    2,
                    Instant.parse("2026-07-04T19:00:00Z"),
                    REPORT_ID,
                    new BigDecimal("81.20"));
        }

        private static DashboardPullRequestDiffFile pullRequestDiffFile() {
            return new DashboardPullRequestDiffFile(
                    "src/App.java",
                    "modified",
                    new CoverageMetricDetails(8, 12),
                    4,
                    0);
        }

        @Override
        public List<DashboardUploadListItem> listUploads(UUID tenantId, UUID repositoryId, String status, int limit) {
            this.tenantId = tenantId;
            this.uploadRepositoryId = repositoryId;
            this.uploadStatus = status;
            this.uploadLimit = limit;
            return List.of(uploadListItem());
        }

        @Override
        public Optional<DashboardUploadDetails> uploadDetails(UUID tenantId, UUID uploadId) {
            this.tenantId = tenantId;
            if (!UPLOAD_ID.equals(uploadId)) {
                return Optional.empty();
            }
            return Optional.of(new DashboardUploadDetails(
                    uploadListItem(),
                    List.of(new DashboardUploadEvent(
                            "upload.received",
                            "{\"analysis_job_id\":\"abc\"}",
                            Instant.parse("2026-07-04T19:00:00Z")))));
        }

        @Override
        public List<DashboardUploadArtifact> uploadArtifacts(UUID tenantId, UUID uploadId) {
            this.tenantId = tenantId;
            return List.of(new DashboardUploadArtifact(
                    "coverage.xml", "coverage", "cobertura", 2048, Instant.parse("2026-07-04T19:00:00Z")));
        }

        private static DashboardUploadListItem uploadListItem() {
            return new DashboardUploadListItem(
                    UPLOAD_ID,
                    REPOSITORY_ID,
                    "abc123",
                    "main",
                    null,
                    "github_actions",
                    "https://ci.example/build-1",
                    "processed",
                    Instant.parse("2026-07-04T18:55:00Z"),
                    Instant.parse("2026-07-04T19:00:00Z"),
                    REPORT_ID,
                    null,
                    null);
        }

        private static DashboardTestRun testRun() {
            return new DashboardTestRun(
                    UUID.fromString("00000000-0000-0000-0000-000000000090"),
                    "unit",
                    "passed",
                    412,
                    410,
                    1,
                    0,
                    1,
                    93210,
                    "abc123",
                    "main",
                    Instant.parse("2026-07-04T19:00:00Z"));
        }
    }
}
