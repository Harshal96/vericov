package dev.vericov.organization.api;

import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.OrganizationException;
import dev.vericov.organization.application.port.InternalServiceAuthorizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@ApplicationScoped
@Path("/internal/v1/control-plane")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalCoverageContextResource {
    private final OrganizationApplicationService organizationService;
    private final InternalServiceAuthorizer internalServiceAuthorizer;

    @Inject
    public InternalCoverageContextResource(
            OrganizationApplicationService organizationService,
            InternalServiceAuthorizer internalServiceAuthorizer) {
        this.organizationService = organizationService;
        this.internalServiceAuthorizer = internalServiceAuthorizer;
    }

    @GET
    @Path("/repositories/{repository_id}/coverage-context")
    public Response getCoverageContext(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @PathParam("repository_id") UUID repositoryId,
            @QueryParam("commit_sha") String commitSha) {
        try {
            internalServiceAuthorizer.requireAuthorizedService(serviceName, serviceToken);
            var context = organizationService.getInternalCoverageContext(repositoryId, commitSha);
            return Response.ok(new ApiResponse<>(RepositoryCoverageContextHttpResponse.from(context))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }
}
