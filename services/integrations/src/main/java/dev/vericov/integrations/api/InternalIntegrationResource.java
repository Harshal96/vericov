package dev.vericov.integrations.api;

import dev.vericov.integrations.application.RecordIntegrationEventCommand;
import dev.vericov.integrations.application.UpdateIntegrationSyncStateCommand;
import dev.vericov.integrations.application.IntegrationApplicationService;
import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.port.InternalServiceAuthorizer;
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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@ApplicationScoped
@Path("/internal/v1/integrations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Internal Integrations", description = "Internal integration resolution and credential APIs")
public class InternalIntegrationResource {
    private static final UUID INTERNAL_REQUESTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final IntegrationApplicationService integrationService;
    private final InternalServiceAuthorizer internalServiceAuthorizer;

    @Inject
    public InternalIntegrationResource(
            IntegrationApplicationService integrationService,
            InternalServiceAuthorizer internalServiceAuthorizer) {
        this.integrationService = integrationService;
        this.internalServiceAuthorizer = internalServiceAuthorizer;
    }

    @GET
    @Path("/connections/{connection_id}")
    @Operation(summary = "Get an integration connection for internal callers")
    @APIResponse(
            responseCode = "200",
            description = "Integration connection",
            content = @Content(schema = @Schema(implementation = IntegrationConnectionHttpResponse.class)))
    public Response getConnection(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @PathParam("connection_id") String connectionId,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            var connectionDetails = integrationService.getConnection(
                    INTERNAL_REQUESTER_ID,
                    tenant,
                    organization,
                    connection);
            return Response.ok(new ApiResponse<>(IntegrationConnectionHttpResponse.from(connectionDetails))).build();
        } catch (IntegrationException exception) {
            return IntegrationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/resolve")
    @Operation(summary = "Resolve an active integration binding for internal callers")
    @APIResponse(
            responseCode = "200",
            description = "Resolved integration",
            content = @Content(schema = @Schema(implementation = ResolvedIntegrationHttpResponse.class)))
    public Response resolve(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId,
            @QueryParam("provider_key") String providerKey,
            @QueryParam("scope_type") String scopeType,
            @QueryParam("scope_id") String scopeId,
            @QueryParam("capability") String capability) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            UUID scope = parseRequiredUuid(scopeId, "scope_id");
            var resolved = integrationService.resolveIntegration(
                    tenant,
                    organization,
                    providerKey,
                    scopeType,
                    scope,
                    capability);
            return Response.ok(new ApiResponse<>(ResolvedIntegrationHttpResponse.from(resolved))).build();
        } catch (IntegrationException exception) {
            return IntegrationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/connections/{connection_id}/credential-leases")
    @Operation(summary = "Create a credential lease for internal callers")
    @APIResponse(
            responseCode = "200",
            description = "Credential lease",
            content = @Content(schema = @Schema(implementation = CredentialLeaseHttpResponse.class)))
    public Response createCredentialLease(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @PathParam("connection_id") String connectionId,
            CreateCredentialLeaseHttpRequest request) {
        try {
            String requester = requireAuthorizedService(serviceName, serviceToken);
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            CreateCredentialLeaseHttpRequest body = requireRequest(request);
            UUID tenant = parseRequiredUuid(body.tenantId(), "tenant_id");
            UUID organization = parseRequiredUuid(body.orgId(), "org_id");
            var lease = integrationService.leaseCredential(
                    requester,
                    tenant,
                    organization,
                    connection,
                    body.credentialKind());
            return Response.ok(new ApiResponse<>(CredentialLeaseHttpResponse.from(lease))).build();
        } catch (IntegrationException exception) {
            return IntegrationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/connections/{connection_id}/sync-state")
    @Operation(summary = "Accept integration sync state for internal callers")
    @APIResponse(responseCode = "202", description = "Sync state accepted")
    public Response acceptSyncState(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @PathParam("connection_id") String connectionId,
            UpdateIntegrationSyncStateHttpRequest request) {
        try {
            String requester = requireAuthorizedService(serviceName, serviceToken);
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            UpdateIntegrationSyncStateHttpRequest body = requireRequest(request);
            UUID tenant = parseRequiredUuid(body.tenantId(), "tenant_id");
            UUID organization = parseRequiredUuid(body.orgId(), "org_id");
            UUID scope = parseRequiredUuid(body.scopeId(), "scope_id");
            var syncState = integrationService.updateSyncState(new UpdateIntegrationSyncStateCommand(
                    requester,
                    tenant,
                    organization,
                    connection,
                    body.syncType(),
                    body.scopeType(),
                    scope,
                    body.status(),
                    body.cursor(),
                    body.checkpoint(),
                    body.lastError(),
                    parseOptionalInstant(body.lastStartedAt(), "last_started_at"),
                    parseOptionalInstant(body.lastCompletedAt(), "last_completed_at"),
                    parseOptionalInstant(body.nextRunAt(), "next_run_at"),
                    parseOptionalInstant(body.leaseExpiresAt(), "lease_expires_at")));
            return Response.accepted(new ApiResponse<>(IntegrationSyncStateHttpResponse.from(syncState))).build();
        } catch (IntegrationException exception) {
            return IntegrationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/events")
    @Operation(summary = "Accept integration events for internal callers")
    @APIResponse(responseCode = "202", description = "Event accepted")
    public Response acceptEvent(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            RecordIntegrationEventHttpRequest request) {
        try {
            String requester = requireAuthorizedService(serviceName, serviceToken);
            RecordIntegrationEventHttpRequest body = requireRequest(request);
            UUID tenant = parseRequiredUuid(body.tenantId(), "tenant_id");
            UUID organization = parseRequiredUuid(body.orgId(), "org_id");
            UUID connection = parseRequiredUuid(body.connectionId(), "connection_id");
            UUID scope = parseOptionalUuid(body.scopeId(), "scope_id");
            var event = integrationService.recordEvent(new RecordIntegrationEventCommand(
                    requester,
                    tenant,
                    organization,
                    connection,
                    body.providerKey(),
                    body.eventType(),
                    body.externalEventId(),
                    body.scopeType(),
                    scope,
                    body.status(),
                    body.payload(),
                    body.error(),
                    parseOptionalInstant(body.receivedAt(), "received_at"),
                    parseOptionalInstant(body.processedAt(), "processed_at")));
            return Response.accepted(new ApiResponse<>(IntegrationEventHttpResponse.from(event))).build();
        } catch (IntegrationException exception) {
            return IntegrationResource.errorResponse(exception);
        }
    }

    private static <T> T requireRequest(T request) {
        if (request == null) {
            throw new IntegrationException("validation_error", "request body is required");
        }
        return request;
    }

    private String requireAuthorizedService(String serviceName, String serviceToken) {
        return internalServiceAuthorizer.requireAuthorizedService(serviceName, serviceToken);
    }

    private static UUID parseRequiredUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException("validation_error", fieldName + " is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IntegrationException("validation_error", fieldName + " is invalid");
        }
    }

    private static UUID parseOptionalUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IntegrationException("validation_error", fieldName + " is invalid");
        }
    }

    private static Instant parseOptionalInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IntegrationException("validation_error", fieldName + " is invalid");
        }
    }

}
