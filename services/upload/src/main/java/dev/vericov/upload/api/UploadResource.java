package dev.vericov.upload.api;

import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.UploadApplicationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@ApplicationScoped
@Path("/api/v1/uploads")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Uploads", description = "Direct coverage and test artifact uploads")
public class UploadResource {
    private final UploadApplicationService uploadService;

    @Inject
    public UploadResource(UploadApplicationService uploadService) {
        this.uploadService = uploadService;
    }

    @POST
    @Operation(summary = "Directly upload coverage/test artifacts and enqueue processing")
    @APIResponse(
            responseCode = "202",
            description = "Upload accepted",
            content = @Content(schema = @Schema(implementation = CreateUploadHttpResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error")
    @APIResponse(responseCode = "401", description = "Authentication required")
    @APIResponse(responseCode = "403", description = "API key not authorized")
    public Response createUpload(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            CreateUploadHttpRequest request) {
        try {
            var accepted = uploadService.acceptUpload(request.toCommand(authorizationHeader, idempotencyKey));
            return Response.accepted(new ApiResponse<>(CreateUploadHttpResponse.from(accepted))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiError.of("validation_error", exception.getMessage()))
                    .build();
        } catch (IllegalStateException exception) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiError.of("artifact_storage_unavailable", "Artifact storage is temporarily unavailable"))
                    .build();
        }
    }

    @GET
    @Path("/{upload_id}")
    @Operation(summary = "Get upload processing status")
    @APIResponse(
            responseCode = "200",
            description = "Upload status",
            content = @Content(schema = @Schema(implementation = UploadStatusHttpResponse.class)))
    @APIResponse(responseCode = "404", description = "Upload not found")
    public Response getUpload(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("upload_id") UUID uploadId) {
        try {
            var upload = uploadService.getUpload(uploadId, authorizationHeader);
            return Response.ok(new ApiResponse<>(UploadStatusHttpResponse.from(upload))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/auth/runner-token")
    @Operation(summary = "Exchange an upload credential for a short-lived runner upload token")
    @APIResponse(
            responseCode = "200",
            description = "Runner upload token",
            content = @Content(schema = @Schema(implementation = RunnerUploadTokenHttpResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required")
    @APIResponse(responseCode = "403", description = "Credential not authorized")
    public Response createRunnerUploadToken(
            @HeaderParam("Authorization") String authorizationHeader,
            CreateRunnerUploadTokenHttpRequest request) {
        try {
            var token = uploadService.createRunnerUploadToken(
                    authorizationHeader,
                    request.repositoryId(),
                    request.branch());
            return Response.ok(new ApiResponse<>(RunnerUploadTokenHttpResponse.from(token))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{upload_id}/artifacts")
    @Operation(summary = "List uploaded artifact metadata")
    @APIResponse(
            responseCode = "200",
            description = "Upload artifacts",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UploadArtifactHttpResponse.class)))
    public Response getArtifacts(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("upload_id") UUID uploadId) {
        try {
            var upload = uploadService.getUpload(uploadId, authorizationHeader);
            var artifacts = upload.artifacts().stream()
                    .map(UploadArtifactHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(artifacts)).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{upload_id}/report")
    @Operation(summary = "Get the analyzed coverage report for an upload")
    @APIResponse(
            responseCode = "200",
            description = "Coverage report",
            content = @Content(schema = @Schema(implementation = CoverageReportHttpResponse.class)))
    @APIResponse(responseCode = "404", description = "Upload or report not found")
    public Response getCoverageReport(
            @HeaderParam("Authorization") String authorizationHeader,
            @PathParam("upload_id") UUID uploadId) {
        try {
            var report = uploadService.getCoverageReport(uploadId, authorizationHeader);
            return Response.ok(new ApiResponse<>(CoverageReportHttpResponse.from(report))).build();
        } catch (InvalidUploadException exception) {
            return errorResponse(exception);
        }
    }

    private static Response errorResponse(InvalidUploadException exception) {
        Response.Status status = switch (exception.code()) {
            case "unauthorized" -> Response.Status.UNAUTHORIZED;
            case "forbidden" -> Response.Status.FORBIDDEN;
            case "not_found" -> Response.Status.NOT_FOUND;
            default -> Response.Status.BAD_REQUEST;
        };
        return Response.status(status)
                .entity(ApiError.of(exception.code(), exception.getMessage()))
                .build();
    }
}
