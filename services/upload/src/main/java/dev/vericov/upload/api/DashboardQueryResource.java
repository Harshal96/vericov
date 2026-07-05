package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardQueryService;
import dev.vericov.upload.application.InvalidUploadException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@ApplicationScoped
@Path("/api/v1/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Dashboard Queries", description = "Tenant-scoped Vericov dashboard data for the veri gateway")
public class DashboardQueryResource {
    private final DashboardQueryService queryService;

    @Inject
    public DashboardQueryResource(DashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @GET
    @Path("/overview")
    @Operation(summary = "Get tenant-wide Vericov coverage overview")
    public Response overview(@HeaderParam("Authorization") String authorizationHeader) {
        try {
            return Response.ok(new ApiResponse<>(
                    DashboardOverviewHttpResponse.from(queryService.overview(authorizationHeader)))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    private static Response errorResponse(InvalidUploadException exception) {
        Response.Status status = switch (exception.code()) {
            case "unauthorized" -> Response.Status.UNAUTHORIZED;
            case "tenant_not_provisioned", "forbidden" -> Response.Status.FORBIDDEN;
            default -> Response.Status.BAD_REQUEST;
        };
        return Response.status(status)
                .entity(ApiError.of(exception.code(), exception.getMessage()))
                .build();
    }
}
