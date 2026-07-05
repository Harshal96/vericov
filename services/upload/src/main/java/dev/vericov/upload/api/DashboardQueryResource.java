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

    @GET
    @Path("/repos/{repo_id}/trend")
    @Operation(summary = "Get a repository coverage trend")
    public Response trend(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("repo_id") java.util.UUID repositoryId,
            @QueryParam("branch") String branch,
            @QueryParam("limit") Integer limit) {
        try {
            var points = queryService.trend(authorizationHeader, repositoryId, branch, limit).stream()
                    .map(DashboardTrendPointHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(new DashboardTrendHttpResponse(points))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/repos/{repo_id}/reports")
    @Operation(summary = "List a repository's recent coverage reports")
    public Response reports(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("repo_id") java.util.UUID repositoryId,
            @QueryParam("limit") Integer limit) {
        try {
            var reports = queryService.reports(authorizationHeader, repositoryId, limit).stream()
                    .map(DashboardReportListItemHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(new DashboardReportListHttpResponse(reports))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/reports/{report_id}")
    @Operation(summary = "Get a tenant-scoped coverage report by id")
    public Response report(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("report_id") java.util.UUID reportId) {
        try {
            return Response.ok(new ApiResponse<>(
                    DashboardReportDetailsHttpResponse.from(queryService.report(authorizationHeader, reportId))))
                    .build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/reports/{report_id}/files")
    @Operation(summary = "List file coverage summaries for a tenant-scoped report")
    public Response reportFiles(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("report_id") java.util.UUID reportId) {
        try {
            var result = queryService.reportFiles(authorizationHeader, reportId);
            var files = result.files().stream().map(DashboardFileSummaryHttpResponse::from).toList();
            return Response.ok(new ApiResponse<>(
                    new DashboardFileSummaryListHttpResponse(files, result.nextCursor()))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/reports/{report_id}/lines")
    @Operation(summary = "List line hit counts for one file in a tenant-scoped report")
    public Response reportLineHits(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("report_id") java.util.UUID reportId,
            @QueryParam("file_path") String filePath) {
        try {
            var lines = queryService.reportLineHits(authorizationHeader, reportId, filePath).stream()
                    .map(DashboardFileLineHitHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(new DashboardFileLineHitListHttpResponse(lines))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/reports/{report_id}/components")
    @Operation(summary = "List flat component coverage rollups for a tenant-scoped report")
    public Response reportComponents(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("report_id") java.util.UUID reportId) {
        try {
            var components = queryService.reportComponents(authorizationHeader, reportId).stream()
                    .map(DashboardComponentRollupHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(new DashboardComponentRollupListHttpResponse(components))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    private static Response errorResponse(InvalidUploadException exception) {
        Response.Status status = switch (exception.code()) {
            case "unauthorized" -> Response.Status.UNAUTHORIZED;
            case "tenant_not_provisioned", "forbidden" -> Response.Status.FORBIDDEN;
            case "repo_not_found", "report_not_found", "file_not_found" -> Response.Status.NOT_FOUND;
            default -> Response.Status.BAD_REQUEST;
        };
        if (exception instanceof DashboardQueryService.FileNotFoundQueryException fileNotFound) {
            return Response.status(status)
                    .entity(new ApiError(new ApiError.ErrorBody(
                            exception.code(),
                            exception.getMessage(),
                            fileNotFound.didYouMean().stream()
                                    .map(candidate -> new ApiError.FieldError("path", "did_you_mean", candidate))
                                    .toList())))
                    .build();
        }
        return Response.status(status)
                .entity(ApiError.of(exception.code(), exception.getMessage()))
                .build();
    }
}
