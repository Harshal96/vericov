package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardQueryService;
import dev.vericov.upload.application.InvalidUploadException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
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

    @GET
    @Path("/repos")
    @Operation(summary = "List tenant repositories with latest default-branch coverage")
    public Response repositories(@HeaderParam("Authorization") String authorizationHeader) {
        try {
            var repositories = queryService.repositories(authorizationHeader).stream()
                    .map(DashboardRepositoryOverviewHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(new DashboardRepositoryListHttpResponse(repositories))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/sparklines")
    @Operation(summary = "Get tenant repository default-branch coverage sparklines")
    public Response sparklines(
            @HeaderParam("Authorization") String authorizationHeader,
            @QueryParam("per_repo") Integer perRepository) {
        try {
            var sparklines = queryService.sparklines(authorizationHeader, perRepository).entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            java.util.Map.Entry::getValue));
            return Response.ok(new ApiResponse<>(new DashboardSparklinesHttpResponse(sparklines))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/repos/{repo_id}")
    @Operation(summary = "Get a tenant repository by id")
    public Response repository(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("repo_id") java.util.UUID repositoryId) {
        try {
            return Response.ok(new ApiResponse<>(
                    DashboardRepositoryHttpResponse.from(queryService.repository(authorizationHeader, repositoryId))))
                    .build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    private static Response errorResponse(InvalidUploadException exception) {
        Response.Status status = switch (exception.code()) {
            case "unauthorized" -> Response.Status.UNAUTHORIZED;
            case "tenant_not_provisioned", "forbidden" -> Response.Status.FORBIDDEN;
            case "repo_not_found" -> Response.Status.NOT_FOUND;
            default -> Response.Status.BAD_REQUEST;
        };
        return Response.status(status)
                .entity(ApiError.of(exception.code(), exception.getMessage()))
                .build();
    }
}
