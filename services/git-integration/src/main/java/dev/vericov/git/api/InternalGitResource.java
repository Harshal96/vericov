package dev.vericov.git.api;

import dev.vericov.git.application.CreateBranchCommand;
import dev.vericov.git.application.CreateOrUpdateCheckRunCommand;
import dev.vericov.git.application.CreateOrUpdatePrAnnotationsCommand;
import dev.vericov.git.application.CreateOrUpdatePrCommentCommand;
import dev.vericov.git.application.GetPullRequestDiffCommand;
import dev.vericov.git.application.GitAnnotationInput;
import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitProviderActionService;
import dev.vericov.git.application.GitProviderQueryService;
import dev.vericov.git.application.OpenPullRequestCommand;
import dev.vericov.git.application.port.InternalServiceAuthorizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/internal/v1/git")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalGitResource {
    private final GitProviderActionService actionService;
    private final GitProviderQueryService queryService;
    private final InternalServiceAuthorizer authorizer;

    @Inject
    public InternalGitResource(
            GitProviderActionService actionService,
            GitProviderQueryService queryService,
            InternalServiceAuthorizer authorizer) {
        this.actionService = actionService;
        this.queryService = queryService;
        this.authorizer = authorizer;
    }

    public InternalGitResource(GitProviderActionService actionService, InternalServiceAuthorizer authorizer) {
        this(actionService, null, authorizer);
    }

    @POST
    @Path("/check-runs")
    public Response createCheckRun(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            CreateCheckRunHttpRequest request) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            CreateCheckRunHttpRequest body = requireRequest(request);
            List<GitAnnotationInput> annotations = body.annotations() == null
                    ? List.of()
                    : body.annotations().stream().map(GitAnnotationHttpRequest::toInput).toList();
            actionService.createOrUpdateCheckRun(new CreateOrUpdateCheckRunCommand(
                    parseUuid(body.tenantId(), "tenant_id"),
                    parseUuid(body.orgId(), "org_id"),
                    parseUuid(body.repositoryId(), "repository_id"),
                    body.providerKey(),
                    body.name(),
                    body.commitSha(),
                    body.status(),
                    body.conclusion(),
                    body.summary(),
                    body.text(),
                    body.detailsUrl(),
                    annotations,
                    body.idempotencyKey()));
            return accepted();
        } catch (GitIntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/pr-comments")
    public Response createPrComment(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            CreatePrCommentHttpRequest request) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            CreatePrCommentHttpRequest body = requireRequest(request);
            actionService.createOrUpdatePrComment(new CreateOrUpdatePrCommentCommand(
                    parseUuid(body.tenantId(), "tenant_id"),
                    parseUuid(body.orgId(), "org_id"),
                    parseUuid(body.repositoryId(), "repository_id"),
                    body.providerKey(),
                    body.pullRequestNumber(),
                    body.marker(),
                    body.body()));
            return accepted();
        } catch (GitIntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/pr-annotations")
    public Response createPrAnnotations(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            CreatePrAnnotationsHttpRequest request) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            CreatePrAnnotationsHttpRequest body = requireRequest(request);
            List<GitAnnotationInput> annotations = body.annotations() == null
                    ? List.of()
                    : body.annotations().stream().map(GitAnnotationHttpRequest::toInput).toList();
            actionService.createOrUpdatePrAnnotations(new CreateOrUpdatePrAnnotationsCommand(
                    parseUuid(body.tenantId(), "tenant_id"),
                    parseUuid(body.orgId(), "org_id"),
                    parseUuid(body.repositoryId(), "repository_id"),
                    body.providerKey(),
                    body.pullRequestNumber(),
                    body.annotationBatchKey(),
                    annotations));
            return accepted();
        } catch (GitIntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/branches")
    public Response createBranch(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            CreateBranchHttpRequest request) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            CreateBranchHttpRequest body = requireRequest(request);
            actionService.createBranch(new CreateBranchCommand(
                    parseUuid(body.tenantId(), "tenant_id"),
                    parseUuid(body.orgId(), "org_id"),
                    parseUuid(body.repositoryId(), "repository_id"),
                    body.providerKey(),
                    body.branchName(),
                    body.baseSha(),
                    body.idempotencyKey()));
            return accepted();
        } catch (GitIntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/pull-requests")
    public Response openPullRequest(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            OpenPullRequestHttpRequest request) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            OpenPullRequestHttpRequest body = requireRequest(request);
            actionService.openPullRequest(new OpenPullRequestCommand(
                    parseUuid(body.tenantId(), "tenant_id"),
                    parseUuid(body.orgId(), "org_id"),
                    parseUuid(body.repositoryId(), "repository_id"),
                    body.providerKey(),
                    body.sourceBranch(),
                    body.targetBranch(),
                    body.title(),
                    body.body(),
                    body.draft(),
                    body.idempotencyKey()));
            return accepted();
        } catch (GitIntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/repositories/{repository_id}/pull-requests/{number}/diff")
    public Response getPullRequestDiff(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @PathParam("repository_id") UUID repositoryId,
            @PathParam("number") int pullRequestNumber,
            @QueryParam("tenant_id") UUID tenantId,
            @QueryParam("org_id") UUID orgId,
            @QueryParam("provider") String providerKey,
            @QueryParam("base_sha") String baseSha,
            @QueryParam("head_sha") String headSha) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            var diff = queryService.getPullRequestDiff(new GetPullRequestDiffCommand(
                    tenantId,
                    orgId,
                    repositoryId,
                    providerKey,
                    pullRequestNumber,
                    baseSha,
                    headSha));
            return Response.ok(new ApiResponse<>(PullRequestDiffHttpResponse.from(diff))).build();
        } catch (GitIntegrationException exception) {
            return errorResponse(exception);
        }
    }

    private String requireAuthorizedService(String serviceName, String serviceToken) {
        try {
            return authorizer.requireAuthorizedService(serviceName, serviceToken);
        } catch (GitIntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GitIntegrationException("unauthorized", exception.getMessage());
        }
    }

    private static Response accepted() {
        return Response.accepted(new ApiResponse<>(new GitActionHttpResponse("accepted"))).build();
    }

    private static <T> T requireRequest(T request) {
        if (request == null) {
            throw new GitIntegrationException("validation_error", "request body is required");
        }
        return request;
    }

    private static UUID parseUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new GitIntegrationException("validation_error", fieldName + " is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new GitIntegrationException("validation_error", fieldName + " is invalid");
        }
    }

    static Response errorResponse(GitIntegrationException exception) {
        Response.Status status = switch (exception.code()) {
            case "unauthorized" -> Response.Status.UNAUTHORIZED;
            case "forbidden" -> Response.Status.FORBIDDEN;
            case "not_found" -> Response.Status.NOT_FOUND;
            case "conflict" -> Response.Status.CONFLICT;
            default -> Response.Status.BAD_REQUEST;
        };
        return Response.status(status)
                .entity(ApiError.of(exception.code(), exception.getMessage()))
                .build();
    }
}
