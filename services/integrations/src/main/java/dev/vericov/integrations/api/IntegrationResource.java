package dev.vericov.integrations.api;

import dev.vericov.integrations.application.IntegrationApplicationService;
import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.port.IntegrationAuthorizer;
import dev.vericov.integrations.application.port.ProviderRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@ApplicationScoped
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Integrations", description = "Integration provider, connection, and binding configuration")
public class IntegrationResource {
    private final IntegrationApplicationService integrationService;
    private final ProviderRegistry providerRegistry;
    private final IntegrationAuthorizer authorizer;

    @Inject
    public IntegrationResource(
            IntegrationApplicationService integrationService,
            ProviderRegistry providerRegistry,
            IntegrationAuthorizer authorizer) {
        this.integrationService = integrationService;
        this.providerRegistry = providerRegistry;
        this.authorizer = authorizer;
    }

    @GET
    @Path("/integration-providers")
    @Operation(summary = "List supported integration providers")
    @APIResponse(
            responseCode = "200",
            description = "Integration providers",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ProviderDefinitionHttpResponse.class)))
    public Response listProviders(@QueryParam("type") String type) {
        var providers = providerRegistry.listProviders(type).stream()
                .map(ProviderDefinitionHttpResponse::from)
                .toList();
        return Response.ok(new ApiResponse<>(providers)).build();
    }

    @GET
    @Path("/orgs/{org_id}/integrations")
    @Operation(summary = "List integration connections for an organization")
    @APIResponse(
            responseCode = "200",
            description = "Integration connections",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = IntegrationConnectionHttpResponse.class)))
    public Response listConnections(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("org_id") String orgId,
            @QueryParam("tenant_id") String tenantId) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:list");
            var connections = integrationService.listConnections(requester, tenant, organization).stream()
                    .map(IntegrationConnectionHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(connections)).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/orgs/{org_id}/integrations")
    @Operation(summary = "Create an integration connection")
    @APIResponse(
            responseCode = "201",
            description = "Integration connection created",
            content = @Content(schema = @Schema(implementation = IntegrationConnectionHttpResponse.class)))
    @APIResponse(responseCode = "409", description = "Integration connection already exists")
    public Response createConnection(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("org_id") String orgId,
            CreateIntegrationConnectionHttpRequest request) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID organization = parseRequiredUuid(orgId, "org_id");
            CreateIntegrationConnectionHttpRequest body = requireRequest(request);
            UUID tenant = requireUuid(body.tenantId(), "tenant_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:create");
            var connection = integrationService.createConnection(body.toCommand(requester, organization));
            return Response.created(URI.create(
                            "/api/v1/integrations/" + connection.id()
                                    + "?tenant_id=" + connection.tenantId()
                                    + "&org_id=" + connection.orgId()))
                    .entity(new ApiResponse<>(IntegrationConnectionHttpResponse.from(connection)))
                    .build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/integrations/{connection_id}")
    @Operation(summary = "Get an integration connection")
    @APIResponse(
            responseCode = "200",
            description = "Integration connection",
            content = @Content(schema = @Schema(implementation = IntegrationConnectionHttpResponse.class)))
    public Response getConnection(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:get");
            var connectionDetails = integrationService.getConnection(requester, tenant, organization, connection);
            return Response.ok(new ApiResponse<>(IntegrationConnectionHttpResponse.from(connectionDetails))).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @PATCH
    @Path("/integrations/{connection_id}")
    @Operation(summary = "Update an integration connection")
    @APIResponse(
            responseCode = "200",
            description = "Updated integration connection",
            content = @Content(schema = @Schema(implementation = IntegrationConnectionHttpResponse.class)))
    public Response updateConnection(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            UpdateIntegrationConnectionHttpRequest request) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            UpdateIntegrationConnectionHttpRequest body = requireRequest(request);
            UUID tenant = requireUuid(body.tenantId(), "tenant_id");
            UUID organization = requireUuid(body.orgId(), "org_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:update");
            var connectionDetails = integrationService.updateConnection(body.toCommand(requester, connection));
            return Response.ok(new ApiResponse<>(IntegrationConnectionHttpResponse.from(connectionDetails))).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/integrations/{connection_id}/disable")
    @Operation(summary = "Disable an integration connection")
    @APIResponse(
            responseCode = "200",
            description = "Disabled integration connection",
            content = @Content(schema = @Schema(implementation = IntegrationConnectionHttpResponse.class)))
    public Response disableConnection(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            DisableIntegrationConnectionHttpRequest request) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            DisableIntegrationConnectionHttpRequest body = requireRequest(request);
            UUID tenant = requireUuid(body.tenantId(), "tenant_id");
            UUID organization = requireUuid(body.orgId(), "org_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:disable");
            var connectionDetails = integrationService.disableConnection(
                    requester,
                    tenant,
                    organization,
                    connection,
                    body.expectedUpdatedAt());
            return Response.ok(new ApiResponse<>(IntegrationConnectionHttpResponse.from(connectionDetails))).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/integrations/{connection_id}/credentials")
    @Operation(summary = "Create integration credential metadata")
    @APIResponse(
            responseCode = "201",
            description = "Integration credential created",
            content = @Content(schema = @Schema(implementation = IntegrationCredentialHttpResponse.class)))
    public Response createCredential(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            CreateIntegrationCredentialHttpRequest request) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            CreateIntegrationCredentialHttpRequest body = requireRequest(request);
            UUID tenant = requireUuid(body.tenantId(), "tenant_id");
            UUID organization = requireUuid(body.orgId(), "org_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:credentials:create");
            var credential = integrationService.createCredential(body.toCommand(requester, connection));
            return Response.created(URI.create(
                            "/api/v1/integrations/" + connection + "/credentials/" + credential.id()))
                    .entity(new ApiResponse<>(IntegrationCredentialHttpResponse.from(credential)))
                    .build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/integrations/{connection_id}/credentials")
    @Operation(summary = "List integration credential metadata")
    @APIResponse(
            responseCode = "200",
            description = "Integration credentials",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = IntegrationCredentialHttpResponse.class)))
    public Response listCredentials(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:credentials:list");
            var credentials = integrationService.listCredentials(requester, tenant, organization, connection).stream()
                    .map(IntegrationCredentialHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(credentials)).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/integrations/{connection_id}/webhook-endpoints")
    @Operation(summary = "Create integration webhook endpoint metadata")
    @APIResponse(
            responseCode = "201",
            description = "Integration webhook endpoint created",
            content = @Content(schema = @Schema(implementation = IntegrationWebhookEndpointHttpResponse.class)))
    public Response createWebhookEndpoint(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            CreateIntegrationWebhookEndpointHttpRequest request) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            CreateIntegrationWebhookEndpointHttpRequest body = requireRequest(request);
            UUID tenant = requireUuid(body.tenantId(), "tenant_id");
            UUID organization = requireUuid(body.orgId(), "org_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:webhooks:create");
            var endpoint = integrationService.createWebhookEndpoint(body.toCommand(requester, connection));
            return Response.created(URI.create(
                            "/api/v1/integrations/" + connection + "/webhook-endpoints/" + endpoint.id()))
                    .entity(new ApiResponse<>(IntegrationWebhookEndpointHttpResponse.from(endpoint)))
                    .build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/integrations/{connection_id}/webhook-endpoints")
    @Operation(summary = "List integration webhook endpoint metadata")
    @APIResponse(
            responseCode = "200",
            description = "Integration webhook endpoints",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = IntegrationWebhookEndpointHttpResponse.class)))
    public Response listWebhookEndpoints(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:webhooks:list");
            var endpoints = integrationService.listWebhookEndpoints(requester, tenant, organization, connection).stream()
                    .map(IntegrationWebhookEndpointHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(endpoints)).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/integrations/{connection_id}/bindings")
    @Operation(summary = "List integration bindings")
    @APIResponse(
            responseCode = "200",
            description = "Integration bindings",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = IntegrationBindingHttpResponse.class)))
    public Response listBindings(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:bindings:list");
            var bindings = integrationService.listBindings(requester, tenant, organization, connection).stream()
                    .map(IntegrationBindingHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(bindings)).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @PUT
    @Path("/integrations/{connection_id}/bindings/{scope_type}/{scope_id}")
    @Operation(summary = "Create or update an integration binding")
    @APIResponse(
            responseCode = "200",
            description = "Integration binding",
            content = @Content(schema = @Schema(implementation = IntegrationBindingHttpResponse.class)))
    public Response upsertBinding(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            @PathParam("scope_type") String scopeType,
            @PathParam("scope_id") String scopeId,
            UpsertIntegrationBindingHttpRequest request) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            UUID scope = parseRequiredUuid(scopeId, "scope_id");
            UpsertIntegrationBindingHttpRequest body = requireRequest(request);
            UUID tenant = requireUuid(body.tenantId(), "tenant_id");
            UUID organization = requireUuid(body.orgId(), "org_id");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:bindings:upsert");
            var binding = integrationService.upsertBinding(
                    body.toCommand(requester, connection, scopeType, scope));
            return Response.ok(new ApiResponse<>(IntegrationBindingHttpResponse.from(binding))).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    @DELETE
    @Path("/integrations/{connection_id}/bindings/{scope_type}/{scope_id}")
    @Operation(summary = "Disable an integration binding")
    @APIResponse(
            responseCode = "200",
            description = "Disabled integration binding",
            content = @Content(schema = @Schema(implementation = IntegrationBindingHttpResponse.class)))
    public Response disableBinding(
            @HeaderParam("X-Vericov-User-Id") String requesterUserId,
            @PathParam("connection_id") String connectionId,
            @PathParam("scope_type") String scopeType,
            @PathParam("scope_id") String scopeId,
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("org_id") String orgId,
            @QueryParam("expected_updated_at") String expectedUpdatedAt) {
        try {
            UUID requester = parseRequesterUserId(requesterUserId);
            UUID tenant = parseRequiredUuid(tenantId, "tenant_id");
            UUID organization = parseRequiredUuid(orgId, "org_id");
            UUID connection = parseRequiredUuid(connectionId, "connection_id");
            UUID scope = parseRequiredUuid(scopeId, "scope_id");
            Instant expected = parseRequiredInstant(expectedUpdatedAt, "expected_updated_at");
            authorizer.requireOrgAccess(requester, tenant, organization, "integrations:bindings:disable");
            var binding = integrationService.disableBinding(
                    requester,
                    tenant,
                    organization,
                    connection,
                    scopeType,
                    scope,
                    expected);
            return Response.ok(new ApiResponse<>(IntegrationBindingHttpResponse.from(binding))).build();
        } catch (IntegrationException exception) {
            return errorResponse(exception);
        }
    }

    private static <T> T requireRequest(T request) {
        if (request == null) {
            throw new IntegrationException("validation_error", "request body is required");
        }
        return request;
    }

    private static UUID parseRequesterUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException("unauthorized", "X-Vericov-User-Id is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IntegrationException("unauthorized", "X-Vericov-User-Id is invalid");
        }
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

    private static UUID requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IntegrationException("validation_error", fieldName + " is required");
        }
        return value;
    }

    private static Instant parseRequiredInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException("validation_error", fieldName + " is required");
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IntegrationException("validation_error", fieldName + " is invalid");
        }
    }

    static Response errorResponse(IntegrationException exception) {
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
