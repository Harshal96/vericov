package dev.vericov.git.api;

import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitWebhookCommand;
import dev.vericov.git.application.GitWebhookService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@Path("/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GitWebhookResource {
    private final GitWebhookService webhookService;

    @Inject
    public GitWebhookResource(GitWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @POST
    @Path("/{provider_key}")
    public Response receive(
            @PathParam("provider_key") String providerKey,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId,
            @QueryParam("repository_id") String repositoryId,
            @QueryParam("connection_id") String connectionId,
            @QueryParam("webhook_endpoint_id") String webhookEndpointId,
            @HeaderParam("X-GitHub-Delivery") String githubDeliveryId,
            @HeaderParam("X-GitHub-Event") String githubEventType,
            @HeaderParam("X-Hub-Signature-256") String githubSignature,
            String body) {
        try {
            GitWebhookCommand command = new GitWebhookCommand(
                    parseOptionalUuid(tenantId, "tenant_id"),
                    parseOptionalUuid(orgId, "org_id"),
                    parseOptionalUuid(repositoryId, "repository_id"),
                    parseOptionalUuid(connectionId, "connection_id"),
                    parseOptionalUuid(webhookEndpointId, "webhook_endpoint_id"),
                    providerKey,
                    eventType(providerKey, githubEventType),
                    deliveryId(providerKey, githubDeliveryId),
                    signature(providerKey, githubSignature),
                    payload(body),
                    Instant.now());
            return Response.accepted(new ApiResponse<>(GitWebhookHttpResponse.from(webhookService.receive(command))))
                    .build();
        } catch (GitIntegrationException exception) {
            return InternalGitResource.errorResponse(exception);
        }
    }

    private static UUID parseOptionalUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new GitIntegrationException("validation_error", fieldName + " is invalid");
        }
    }

    private static String eventType(String providerKey, String githubEventType) {
        if ("github".equalsIgnoreCase(providerKey == null ? "" : providerKey.trim())) {
            return requireHeader(githubEventType, "X-GitHub-Event");
        }
        throw new GitIntegrationException("unsupported_provider", "Webhook provider is not supported");
    }

    private static String deliveryId(String providerKey, String githubDeliveryId) {
        if ("github".equalsIgnoreCase(providerKey == null ? "" : providerKey.trim())) {
            return requireHeader(githubDeliveryId, "X-GitHub-Delivery");
        }
        throw new GitIntegrationException("unsupported_provider", "Webhook provider is not supported");
    }

    private static String signature(String providerKey, String githubSignature) {
        if ("github".equalsIgnoreCase(providerKey == null ? "" : providerKey.trim())) {
            return requireHeader(githubSignature, "X-Hub-Signature-256");
        }
        throw new GitIntegrationException("unsupported_provider", "Webhook provider is not supported");
    }

    private static byte[] payload(String body) {
        if (body == null || body.isBlank()) {
            throw new GitIntegrationException("validation_error", "payload is required");
        }
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String requireHeader(String value, String headerName) {
        if (value == null || value.isBlank()) {
            throw new GitIntegrationException("validation_error", headerName + " is required");
        }
        return value.trim();
    }
}
