package dev.vericov.upload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.DashboardReport;
import dev.vericov.upload.application.DashboardReportDetails;
import dev.vericov.upload.application.DashboardReportListItem;
import dev.vericov.upload.application.DashboardRepository;
import dev.vericov.upload.application.DashboardRepositoryOverview;
import dev.vericov.upload.application.DashboardQueryService;
import dev.vericov.upload.application.DashboardTrendPoint;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.port.DashboardQueryRepository;
import dev.vericov.upload.application.port.TenantAuthenticator;
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
        DashboardQueryService service = new DashboardQueryService(authorizationHeader -> principal(), repository);

        service.overview("Bearer internal-token");
        service.repositories("Bearer internal-token");
        service.sparklines("Bearer internal-token", 99);
        service.repository("Bearer internal-token", REPOSITORY_ID);
        service.trend("Bearer internal-token", REPOSITORY_ID, " ", 300);
        service.reports("Bearer internal-token", REPOSITORY_ID, 300);
        service.report("Bearer internal-token", REPORT_ID);

        assertEquals(TENANT_ID, repository.tenantId);
        assertEquals(60, repository.perRepository);
        assertEquals(200, repository.trendLimit);
        assertEquals(100, repository.reportLimit);
        assertEquals(REPOSITORY_ID, repository.repositoryId);
        assertEquals(REPORT_ID, repository.reportId);
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

    private static DashboardQueryResource resource(
            TenantAuthenticator authenticator,
            DashboardQueryRepository repository) {
        return new DashboardQueryResource(new DashboardQueryService(authenticator, repository));
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
        private int perRepository;
        private int trendLimit;
        private int reportLimit;

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
    }
}
