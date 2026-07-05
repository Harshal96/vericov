package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageQueryService;
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
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Read-only coverage queries for agents and other automated tooling. Every
 * endpoint requires the {@code uploads:read} scope and resolves the
 * repository from the presented API key; no repository UUID appears in the
 * path.
 */
@ApplicationScoped
@Path("/api/v1/coverage")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Coverage Queries", description = "Read-only coverage data for agents and automated tooling")
public class CoverageQueryResource {
    private final CoverageQueryService queryService;

    @Inject
    public CoverageQueryResource(CoverageQueryService queryService) {
        this.queryService = queryService;
    }

    @GET
    @Path("/summary")
    @Operation(summary = "Get repository coverage totals for a ref")
    public Response summary(@HeaderParam("Authorization") String authorizationHeader, @QueryParam("ref") String ref) {
        try {
            return Response.ok(new ApiResponse<>(
                    CoverageSummaryHttpResponse.from(queryService.summary(authorizationHeader, ref)))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/components")
    @Operation(summary = "Get the component coverage tree for a ref")
    public Response components(@HeaderParam("Authorization") String authorizationHeader, @QueryParam("ref") String ref) {
        try {
            return Response.ok(new ApiResponse<>(
                    ComponentCoverageListHttpResponse.from(queryService.components(authorizationHeader, ref)))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/files")
    @Operation(summary = "List per-file coverage for a ref, worst-covered first by default")
    public Response files(
            @HeaderParam("Authorization") String authorizationHeader,
            @QueryParam("ref") String ref,
            @QueryParam("path_prefix") String pathPrefix,
            @QueryParam("component") String component,
            @QueryParam("sort") String sort,
            @QueryParam("limit") Integer limit,
            @QueryParam("cursor") String cursor) {
        try {
            return Response.ok(new ApiResponse<>(CoverageFileListHttpResponse.from(
                    queryService.files(authorizationHeader, ref, pathPrefix, component, sort, limit, cursor))))
                    .build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/file")
    @Operation(summary = "Get one file's coverage summary and uncovered line ranges")
    public Response file(
            @HeaderParam("Authorization") String authorizationHeader,
            @QueryParam("ref") String ref,
            @QueryParam("path") String path) {
        try {
            return Response.ok(new ApiResponse<>(
                    CoverageFileHttpResponse.from(queryService.file(authorizationHeader, ref, path)))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/pull-requests/{number}")
    @Operation(summary = "Get patch coverage for a pull request's latest diff-bearing report")
    public Response pullRequestPatch(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("number") int number) {
        try {
            return Response.ok(new ApiResponse<>(PatchCoverageHttpResponse.from(
                    queryService.patchForPullRequest(authorizationHeader, number).patch()))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/gaps")
    @Operation(summary = "List coverage gap findings for a ref, highest risk first by default")
    public Response gaps(
            @HeaderParam("Authorization") String authorizationHeader,
            @QueryParam("ref") String ref,
            @QueryParam("component") String component,
            @QueryParam("min_risk_level") String minRiskLevel,
            @QueryParam("status") String status,
            @QueryParam("limit") Integer limit,
            @QueryParam("cursor") String cursor) {
        try {
            return Response.ok(new ApiResponse<>(CoverageGapListHttpResponse.from(
                    queryService.gaps(authorizationHeader, ref, component, minRiskLevel, status, limit, cursor))))
                    .build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/gates")
    @Operation(summary = "List gate evaluations for a ref")
    public Response gates(@HeaderParam("Authorization") String authorizationHeader, @QueryParam("ref") String ref) {
        try {
            return Response.ok(new ApiResponse<>(
                    CoverageGateListHttpResponse.from(queryService.gates(authorizationHeader, ref)))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/gap-manifest")
    @Operation(summary = "Get a ranked, deterministic manifest of actionable coverage gaps for a ref or pull request")
    public Response gapManifest(
            @HeaderParam("Authorization") String authorizationHeader,
            @QueryParam("ref") String ref,
            @QueryParam("pull_request") Integer pullRequest,
            @QueryParam("next_action") String nextAction,
            @QueryParam("min_risk_level") String minRiskLevel,
            @QueryParam("limit") Integer limit) {
        try {
            return Response.ok(new ApiResponse<>(CoverageGapManifestHttpResponse.from(
                    queryService.manifest(authorizationHeader, ref, pullRequest, nextAction, minRiskLevel, limit))))
                    .build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    private static Response errorResponse(InvalidUploadException exception) {
        Response.Status status = switch (exception.code()) {
            case "unauthorized" -> Response.Status.UNAUTHORIZED;
            case "scope_missing", "forbidden" -> Response.Status.FORBIDDEN;
            case "ref_not_found", "file_not_found", "pull_request_not_found" -> Response.Status.NOT_FOUND;
            default -> Response.Status.BAD_REQUEST;
        };
        if (exception instanceof CoverageQueryService.FileNotFoundQueryException fileNotFound) {
            List<ApiError.FieldError> details = fileNotFound.didYouMean().stream()
                    .map(candidate -> new ApiError.FieldError("path", "did_you_mean", candidate))
                    .toList();
            return Response.status(status)
                    .entity(new ApiError(new ApiError.ErrorBody(exception.code(), exception.getMessage(), details)))
                    .build();
        }
        return Response.status(status)
                .entity(ApiError.of(exception.code(), exception.getMessage()))
                .build();
    }
}
