package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.OrganizationApplicationService;
import dev.vericov.controlplane.application.OrganizationException;
import dev.vericov.controlplane.application.port.UserPrincipalResolver;
import dev.vericov.controlplane.domain.AuthenticatedUser;
import dev.vericov.controlplane.domain.UserAuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {
    private final OrganizationApplicationService organizationService;
    private final UserPrincipalResolver userPrincipalResolver;

    @Inject
    public AuthResource(
            OrganizationApplicationService organizationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.organizationService = organizationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GET
    @Path("/me")
    public Response me(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader) {
        try {
            AuthenticatedUser user = userPrincipalResolver.resolve(new UserAuthContext(authorizationHeader, userIdHeader));
            List<OrganizationHttpResponse> organizations = organizationService.listOrganizations(user.userId()).stream()
                    .map(OrganizationHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(new CurrentUserHttpResponse(
                    user.userId(),
                    user.email(),
                    organizations))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    public record CurrentUserHttpResponse(
            @JsonbProperty("user_id")
            UUID userId,
            String email,
            List<OrganizationHttpResponse> organizations) {
    }
}
