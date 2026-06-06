package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.OrganizationApplicationService;
import dev.vericov.controlplane.application.OrganizationException;
import dev.vericov.controlplane.application.port.UserPrincipalResolver;
import dev.vericov.controlplane.domain.AuthenticatedUser;
import dev.vericov.controlplane.domain.UserAuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@ApplicationScoped
@Path("/internal/v1/orgs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalRepositoryConfigResource {
    private final OrganizationApplicationService organizationService;
    private final UserPrincipalResolver userPrincipalResolver;

    @Inject
    public InternalRepositoryConfigResource(
            OrganizationApplicationService organizationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.organizationService = organizationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/effective-config")
    public Response getEffectiveConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.getEffectiveRepositoryConfig(
                    user.userId(),
                    organizationId,
                    repositoryId);
            return Response.ok(new ApiResponse<>(EffectiveRepositoryConfigHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    private AuthenticatedUser resolveUser(String authorizationHeader, String userIdHeader) {
        return userPrincipalResolver.resolve(new UserAuthContext(authorizationHeader, userIdHeader));
    }
}
