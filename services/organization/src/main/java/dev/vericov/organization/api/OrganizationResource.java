package dev.vericov.organization.api;

import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.OrganizationException;
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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@ApplicationScoped
@Path("/api/v1/orgs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Organizations", description = "Organization and membership management")
public class OrganizationResource {
    private final OrganizationApplicationService organizationService;
    private final UserPrincipalResolver userPrincipalResolver;

    @Inject
    public OrganizationResource(
            OrganizationApplicationService organizationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.organizationService = organizationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GET
    @Operation(summary = "List organizations visible to the current user")
    @APIResponse(
            responseCode = "200",
            description = "Visible organizations",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = OrganizationHttpResponse.class)))
    public Response listOrganizations(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var organizations = organizationService.listOrganizations(user.userId()).stream()
                    .map(OrganizationHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(organizations)).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Operation(summary = "Create an organization")
    @APIResponse(
            responseCode = "201",
            description = "Organization created",
            content = @Content(schema = @Schema(implementation = OrganizationHttpResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error")
    @APIResponse(responseCode = "401", description = "Authentication required")
    @APIResponse(responseCode = "409", description = "Slug already exists")
    public Response createOrganization(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            CreateOrganizationHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var organization = organizationService.createOrganization(request.toCommand(user.userId()));
            var response = OrganizationHttpResponse.from(organization);
            return Response.created(URI.create("/api/v1/orgs/" + organization.id()))
                    .entity(new ApiResponse<>(response))
                    .build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}")
    @Operation(summary = "Get an organization")
    @APIResponse(
            responseCode = "200",
            description = "Organization",
            content = @Content(schema = @Schema(implementation = OrganizationHttpResponse.class)))
    @APIResponse(responseCode = "404", description = "Organization not found")
    public Response getOrganization(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var organization = organizationService.getOrganization(user.userId(), organizationId);
            return Response.ok(new ApiResponse<>(OrganizationHttpResponse.from(organization))).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @PATCH
    @Path("/{org_id}")
    @Operation(summary = "Update an organization")
    @APIResponse(
            responseCode = "200",
            description = "Updated organization",
            content = @Content(schema = @Schema(implementation = OrganizationHttpResponse.class)))
    public Response updateOrganization(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            UpdateOrganizationHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var organization = organizationService.updateOrganization(request.toCommand(user.userId(), organizationId));
            return Response.ok(new ApiResponse<>(OrganizationHttpResponse.from(organization))).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories")
    @Operation(summary = "List repositories registered to an organization")
    @APIResponse(
            responseCode = "200",
            description = "Repositories",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = RepositoryHttpResponse.class)))
    public Response listRepositories(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repositories = organizationService.listRepositories(user.userId(), organizationId).stream()
                    .map(RepositoryHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(repositories)).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories")
    @Operation(summary = "Register a repository under an organization")
    @APIResponse(
            responseCode = "201",
            description = "Repository registered",
            content = @Content(schema = @Schema(implementation = RepositoryHttpResponse.class)))
    public Response registerRepository(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            CreateRepositoryHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repository = organizationService.registerRepository(request.toCommand(user.userId(), organizationId));
            return Response.created(URI.create("/api/v1/orgs/" + organizationId + "/repositories/" + repository.id()))
                    .entity(new ApiResponse<>(RepositoryHttpResponse.from(repository)))
                    .build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}")
    @Operation(summary = "Get an organization repository")
    @APIResponse(
            responseCode = "200",
            description = "Repository",
            content = @Content(schema = @Schema(implementation = RepositoryHttpResponse.class)))
    public Response getRepository(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repository = organizationService.getRepository(user.userId(), organizationId, repositoryId);
            return Response.ok(new ApiResponse<>(RepositoryHttpResponse.from(repository))).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @PATCH
    @Path("/{org_id}/repositories/{repository_id}")
    @Operation(summary = "Update an organization repository")
    @APIResponse(
            responseCode = "200",
            description = "Updated repository",
            content = @Content(schema = @Schema(implementation = RepositoryHttpResponse.class)))
    public Response updateRepository(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            UpdateRepositoryHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repository = organizationService.updateRepository(
                    request.toCommand(user.userId(), organizationId, repositoryId));
            return Response.ok(new ApiResponse<>(RepositoryHttpResponse.from(repository))).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/memberships")
    @Operation(summary = "List organization memberships")
    @APIResponse(
            responseCode = "200",
            description = "Memberships",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = MembershipHttpResponse.class)))
    public Response listMemberships(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var memberships = organizationService.listMemberships(user.userId(), organizationId).stream()
                    .map(MembershipHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(memberships)).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/memberships")
    @Operation(summary = "Add or invite an organization member")
    @APIResponse(
            responseCode = "201",
            description = "Membership created",
            content = @Content(schema = @Schema(implementation = MembershipHttpResponse.class)))
    public Response addMembership(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            CreateMembershipHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var membership = organizationService.addMembership(request.toCommand(user.userId(), organizationId));
            return Response.created(URI.create("/api/v1/orgs/" + organizationId + "/memberships/" + membership.id()))
                    .entity(new ApiResponse<>(MembershipHttpResponse.from(membership)))
                    .build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @PATCH
    @Path("/{org_id}/memberships/{membership_id}")
    @Operation(summary = "Update an organization membership")
    @APIResponse(
            responseCode = "200",
            description = "Updated membership",
            content = @Content(schema = @Schema(implementation = MembershipHttpResponse.class)))
    public Response updateMembership(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("membership_id") UUID membershipId,
            UpdateMembershipHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var membership = organizationService.updateMembership(
                    request.toCommand(user.userId(), organizationId, membershipId));
            return Response.ok(new ApiResponse<>(MembershipHttpResponse.from(membership))).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/invitations")
    @Operation(summary = "List pending and historical organization invitations")
    @APIResponse(
            responseCode = "200",
            description = "Invitations",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = InvitationHttpResponse.class)))
    public Response listInvitations(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var invitations = organizationService.listInvitations(user.userId(), organizationId).stream()
                    .map(InvitationHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(invitations)).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/invitations")
    @Operation(summary = "Invite a user to an organization")
    @APIResponse(
            responseCode = "201",
            description = "Invitation created",
            content = @Content(schema = @Schema(implementation = InvitationHttpResponse.class)))
    public Response inviteMember(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            CreateInvitationHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var invitation = organizationService.inviteMember(request.toCommand(user.userId(), organizationId));
            return Response.created(URI.create("/api/v1/orgs/" + organizationId + "/invitations/" + invitation.id()))
                    .entity(new ApiResponse<>(InvitationHttpResponse.from(invitation)))
                    .build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/invitations/{invitation_id}/accept")
    @Operation(summary = "Accept an organization invitation")
    @APIResponse(
            responseCode = "200",
            description = "Membership activated",
            content = @Content(schema = @Schema(implementation = MembershipHttpResponse.class)))
    public Response acceptInvitation(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("invitation_id") UUID invitationId,
            AcceptInvitationHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var membership = organizationService.acceptInvitation(
                    request.toCommand(user.userId(), user.email(), organizationId, invitationId));
            return Response.ok(new ApiResponse<>(MembershipHttpResponse.from(membership))).build();
        } catch (OrganizationException exception) {
            return errorResponse(exception);
        }
    }

    private AuthenticatedUser resolveUser(String authorizationHeader, String userIdHeader) {
        return userPrincipalResolver.resolve(new UserAuthContext(authorizationHeader, userIdHeader));
    }

    static Response errorResponse(OrganizationException exception) {
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
