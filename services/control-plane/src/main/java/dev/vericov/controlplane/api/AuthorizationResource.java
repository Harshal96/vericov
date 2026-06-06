package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.OrganizationApplicationService;
import dev.vericov.controlplane.application.OrganizationException;
import dev.vericov.controlplane.application.port.UserPrincipalResolver;
import dev.vericov.controlplane.domain.AuthenticatedUser;
import dev.vericov.controlplane.domain.UserAuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/internal/v1/authz")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthorizationResource {
    private final OrganizationApplicationService organizationService;
    private final UserPrincipalResolver userPrincipalResolver;

    @Inject
    public AuthorizationResource(
            OrganizationApplicationService organizationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.organizationService = organizationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @POST
    @Path("/check")
    public Response checkAuthorization(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            AuthorizationCheckHttpRequest request) {
        try {
            AuthenticatedUser user = userPrincipalResolver.resolve(new UserAuthContext(authorizationHeader, userIdHeader));
            var decision = organizationService.checkAuthorization(request.toCommand(user.userId()));
            return Response.ok(new ApiResponse<>(AuthorizationDecisionHttpResponse.from(decision))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }
}
