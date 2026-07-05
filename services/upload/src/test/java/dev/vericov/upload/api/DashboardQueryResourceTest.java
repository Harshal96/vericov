package dev.vericov.upload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.DashboardQueryService;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.port.DashboardQueryRepository;
import dev.vericov.upload.application.port.TenantAuthenticator;
import dev.vericov.upload.domain.TenantPrincipal;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardQueryResourceTest {
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Test
    void overviewReturnsEnvelopeWithSnakeCasePayload() {
        DashboardQueryResource resource = resource(
                authorizationHeader -> principal(),
                tenantId -> new DashboardOverview(8, 6, new BigDecimal("81.40"), 412, 37, 3, 5));

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
                tenantId -> new DashboardOverview(0, 0, null, 0, 0, 0, 0));

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
                tenantId -> new DashboardOverview(0, 0, null, 0, 0, 0, 0));

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

        assertEquals(TENANT_ID, repository.tenantId);
    }

    private static DashboardQueryResource resource(
            TenantAuthenticator authenticator,
            DashboardQueryRepository repository) {
        return new DashboardQueryResource(new DashboardQueryService(authenticator, repository));
    }

    private static TenantPrincipal principal() {
        return new TenantPrincipal(TENANT_ID, ORG_ID, "user-1", Set.of("member"), Set.of("cov:read"));
    }

    private static final class RecordingRepository implements DashboardQueryRepository {
        private UUID tenantId;

        @Override
        public DashboardOverview overview(UUID tenantId) {
            this.tenantId = tenantId;
            return new DashboardOverview(0, 0, null, 0, 0, 0, 0);
        }
    }
}
