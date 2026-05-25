package dev.vericov.git.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@ApplicationScoped
@Path("/api/v1/git")
@Produces(MediaType.APPLICATION_JSON)
public class GitProviderStatusResource {

    @GET
    @Path("/providers")
    public Response providers() {
        return Response.ok(new ApiResponse<>(new GitProviderStatusHttpResponse(List.of(
                new GitProviderStatusHttpResponse.GitProviderStatus(
                        "github",
                        "implemented",
                        "implemented",
                        "implemented"),
                new GitProviderStatusHttpResponse.GitProviderStatus(
                        "gitlab",
                        "unsupported_provider",
                        "unsupported_provider",
                        "unsupported_provider"),
                new GitProviderStatusHttpResponse.GitProviderStatus(
                        "bitbucket",
                        "unsupported_provider",
                        "unsupported_provider",
                        "unsupported_provider"))))).build();
    }
}
