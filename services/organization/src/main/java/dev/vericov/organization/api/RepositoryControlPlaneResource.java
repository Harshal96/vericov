package dev.vericov.organization.api;

import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.OrganizationException;
import dev.vericov.organization.application.GetCoverageBadgeCommand;
import dev.vericov.organization.application.GetCoverageLineHitsQuery;
import dev.vericov.organization.application.GetCommitCoverageReportQuery;
import dev.vericov.organization.application.GetOrganizationDashboardQuery;
import dev.vericov.organization.application.GetPullRequestCoverageReportQuery;
import dev.vericov.organization.application.GetRepositoryDashboardQuery;
import dev.vericov.organization.application.ListCoverageTrendsQuery;
import dev.vericov.organization.application.ListGateEvaluationsQuery;
import dev.vericov.organization.application.ListRepositoryDashboardsQuery;
import dev.vericov.organization.application.RotateRepositoryBadgeTokenCommand;
import dev.vericov.organization.application.UpsertRepositoryGatesCommand;
import dev.vericov.organization.application.ValidateRepositoryConfigCommand;
import dev.vericov.organization.application.port.UserPrincipalResolver;
import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/api/v1/orgs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RepositoryControlPlaneResource {
    private final OrganizationApplicationService organizationService;
    private final UserPrincipalResolver userPrincipalResolver;

    @Inject
    public RepositoryControlPlaneResource(
            OrganizationApplicationService organizationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.organizationService = organizationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GET
    @Path("/{org_id}/policy-defaults")
    public Response getPolicyDefaults(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var defaults = organizationService.getPolicyDefaults(user.userId(), organizationId);
            return Response.ok(new ApiResponse<>(PolicyDefaultsHttpResponse.from(defaults))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PUT
    @Path("/{org_id}/policy-defaults")
    public Response upsertPolicyDefaults(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            PolicyDefaultsHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var defaults = organizationService.upsertPolicyDefaults(request.toCommand(user.userId(), organizationId));
            return Response.ok(new ApiResponse<>(PolicyDefaultsHttpResponse.from(defaults))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/config")
    public Response getEffectiveRepositoryConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.getEffectiveRepositoryConfig(user.userId(), organizationId, repositoryId);
            return Response.ok(new ApiResponse<>(EffectiveRepositoryConfigHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PUT
    @Path("/{org_id}/repositories/{repository_id}/config")
    public Response upsertRepositoryConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            RepositoryConfigHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.upsertRepositoryConfig(
                    request.toCommand(user.userId(), organizationId, repositoryId));
            return Response.ok(new ApiResponse<>(RepositoryConfigHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories/{repository_id}/config/validate")
    public Response validateRepositoryConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            RepositoryConfigHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.validateRepositoryConfig(new ValidateRepositoryConfigCommand(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    request.config(),
                    request.schemaVersion()));
            return Response.ok(new ApiResponse<>(RepositoryConfigValidationHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/policies")
    public Response listRepositoryPolicies(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var policies = organizationService.listRepositoryPolicies(user.userId(), organizationId, repositoryId)
                    .stream()
                    .map(RepositoryPolicyHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(policies)).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories/{repository_id}/policies")
    public Response createRepositoryPolicy(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            RepositoryPolicyHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var policy = organizationService.createRepositoryPolicy(
                    request.toCreateCommand(user.userId(), organizationId, repositoryId));
            return Response.created(URI.create("/api/v1/orgs/" + organizationId
                            + "/repositories/" + repositoryId
                            + "/policies/" + policy.id()))
                    .entity(new ApiResponse<>(RepositoryPolicyHttpResponse.from(policy)))
                    .build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PATCH
    @Path("/{org_id}/repositories/{repository_id}/policies/{policy_id}")
    public Response updateRepositoryPolicy(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @PathParam("policy_id") UUID policyId,
            RepositoryPolicyHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var policy = organizationService.updateRepositoryPolicy(
                    request.toUpdateCommand(user.userId(), organizationId, repositoryId, policyId));
            return Response.ok(new ApiResponse<>(RepositoryPolicyHttpResponse.from(policy))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/gates")
    public Response listRepositoryGates(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var gates = organizationService.listRepositoryGates(user.userId(), organizationId, repositoryId)
                    .stream()
                    .map(RepositoryGateHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(gates)).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PUT
    @Path("/{org_id}/repositories/{repository_id}/gates")
    public Response replaceRepositoryGates(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            List<RepositoryGateHttpRequest> request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repository = organizationService.getRepository(user.userId(), organizationId, repositoryId);
            Instant now = Instant.now();
            var gates = organizationService.replaceRepositoryGates(new UpsertRepositoryGatesCommand(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    request.stream()
                            .map(gate -> gate.toDetails(repository.tenantId(), organizationId, repositoryId, now))
                            .toList()));
            return Response.ok(new ApiResponse<>(gates.stream().map(RepositoryGateHttpResponse::from).toList())).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories/{repository_id}/gates/validate")
    public Response validateRepositoryGates(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            List<RepositoryGateHttpRequest> request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repository = organizationService.getRepository(user.userId(), organizationId, repositoryId);
            Instant now = Instant.now();
            var gates = organizationService.validateRepositoryGates(new UpsertRepositoryGatesCommand(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    request.stream()
                            .map(gate -> gate.toDetails(repository.tenantId(), organizationId, repositoryId, now))
                            .toList()));
            return Response.ok(new ApiResponse<>(gates.stream().map(RepositoryGateHttpResponse::from).toList())).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/commits/{sha}/report")
    public Response getCommitCoverageReport(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @PathParam("sha") String commitSha,
            @QueryParam("include_files") boolean includeFiles,
            @QueryParam("limit") int limit) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var report = organizationService.getCommitCoverageReport(new GetCommitCoverageReportQuery(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    commitSha,
                    includeFiles,
                    limit));
            return Response.ok(new ApiResponse<>(CoverageReportHttpResponse.from(report))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/pull-requests/{number}/report")
    public Response getPullRequestCoverageReport(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @PathParam("number") int pullRequestNumber,
            @QueryParam("include_files") boolean includeFiles,
            @QueryParam("include_diff_lines") boolean includeDiffLines,
            @QueryParam("limit") int limit) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var report = organizationService.getPullRequestCoverageReport(new GetPullRequestCoverageReportQuery(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    pullRequestNumber,
                    includeFiles,
                    limit,
                    includeDiffLines));
            return Response.ok(new ApiResponse<>(PullRequestCoverageReportHttpResponse.from(report))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/commits/{sha}/line-hits")
    public Response getCoverageLineHits(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @PathParam("sha") String commitSha,
            @QueryParam("file_path") String filePath) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var lineHits = organizationService.getCoverageLineHits(new GetCoverageLineHitsQuery(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    commitSha,
                    filePath));
            return Response.ok(new ApiResponse<>(CoverageLineHitMapHttpResponse.from(lineHits))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/trends")
    public Response listCoverageTrends(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @QueryParam("branch") String branch,
            @QueryParam("metric") String metric,
            @QueryParam("limit") int limit) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var trend = organizationService.listCoverageTrends(new ListCoverageTrendsQuery(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    branch,
                    metric,
                    null,
                    null,
                    limit));
            return Response.ok(new ApiResponse<>(CoverageTrendHttpResponse.from(trend))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/gate-evaluations")
    public Response listGateEvaluations(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @QueryParam("branch") String branch,
            @QueryParam("status") String status,
            @QueryParam("limit") int limit) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var evaluations = organizationService.listGateEvaluations(new ListGateEvaluationsQuery(
                            user.userId(),
                            organizationId,
                            repositoryId,
                            branch,
                            status,
                            limit))
                    .stream()
                    .map(GateEvaluationHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(evaluations)).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/dashboard")
    public Response getRepositoryDashboard(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @QueryParam("branch") String branch) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var dashboard = organizationService.getRepositoryDashboard(new GetRepositoryDashboardQuery(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    branch));
            return Response.ok(new ApiResponse<>(RepositoryDashboardHttpResponse.from(dashboard))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/dashboard")
    public Response getOrganizationDashboard(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @QueryParam("branch") String branch) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var dashboard = organizationService.getOrganizationDashboard(new GetOrganizationDashboardQuery(
                    user.userId(),
                    organizationId,
                    branch));
            return Response.ok(new ApiResponse<>(OrganizationDashboardHttpResponse.from(dashboard))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/dashboard")
    public Response listRepositoryDashboards(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @QueryParam("branch") String branch) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var dashboards = organizationService.listRepositoryDashboards(new ListRepositoryDashboardsQuery(
                            user.userId(),
                            organizationId,
                            branch))
                    .stream()
                    .map(RepositoryDashboardSummaryHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(dashboards)).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/badge-settings")
    public Response getRepositoryBadgeSettings(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var settings = organizationService.getRepositoryBadgeSettings(
                    user.userId(),
                    organizationId,
                    repositoryId);
            return Response.ok(new ApiResponse<>(RepositoryBadgeSettingsHttpResponse.from(settings))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PUT
    @Path("/{org_id}/repositories/{repository_id}/badge-settings")
    public Response upsertRepositoryBadgeSettings(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            RepositoryBadgeSettingsHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var settings = organizationService.upsertRepositoryBadgeSettings(
                    request.toCommand(user.userId(), organizationId, repositoryId));
            return Response.ok(new ApiResponse<>(RepositoryBadgeSettingsHttpResponse.from(settings))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories/{repository_id}/badge-settings/rotate-token")
    public Response rotateRepositoryBadgeToken(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var token = organizationService.rotateRepositoryBadgeToken(new RotateRepositoryBadgeTokenCommand(
                    user.userId(),
                    organizationId,
                    repositoryId));
            return Response.ok(new ApiResponse<>(BadgeTokenHttpResponse.from(token))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/badges/coverage.svg")
    @Produces("image/svg+xml")
    public Response getCoverageBadgeSvg(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @QueryParam("token") String token,
            @QueryParam("branch") String branch,
            @QueryParam("metric") String metric,
            @QueryParam("style") String style) {
        try {
            UUID requesterUserId = resolveUserIdForBadge(authorizationHeader, userIdHeader, token);
            var badge = organizationService.getCoverageBadge(new GetCoverageBadgeCommand(
                    requesterUserId,
                    organizationId,
                    repositoryId,
                    token,
                    branch,
                    metric));
            return Response.ok(CoverageBadgeSvgRenderer.render(badge, style), "image/svg+xml")
                    .header("Cache-Control", cacheControl(token))
                    .build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/badges/coverage.json")
    public Response getCoverageBadgeJson(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @QueryParam("token") String token,
            @QueryParam("branch") String branch,
            @QueryParam("metric") String metric) {
        try {
            UUID requesterUserId = resolveUserIdForBadge(authorizationHeader, userIdHeader, token);
            var badge = organizationService.getCoverageBadge(new GetCoverageBadgeCommand(
                    requesterUserId,
                    organizationId,
                    repositoryId,
                    token,
                    branch,
                    metric));
            return Response.ok(new ApiResponse<>(CoverageBadgeHttpResponse.from(badge)))
                    .header("Cache-Control", cacheControl(token))
                    .build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    private UUID resolveUserIdForBadge(String authorizationHeader, String userIdHeader, String token) {
        if (token != null && !token.isBlank()) {
            return null;
        }
        return resolveUser(authorizationHeader, userIdHeader).userId();
    }

    private static String cacheControl(String token) {
        return token != null && !token.isBlank()
                ? "public, max-age=60"
                : "private, max-age=30";
    }

    private AuthenticatedUser resolveUser(String authorizationHeader, String userIdHeader) {
        return userPrincipalResolver.resolve(new UserAuthContext(authorizationHeader, userIdHeader));
    }
}
