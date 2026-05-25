package dev.vericov.organization.api;

import dev.vericov.organization.application.InMemoryOrganizationRepository;
import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.port.UserPrincipalResolver;
import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OrganizationResourceIntegrationTest {
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VIEWER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INVITED_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void invitedUserAcceptsMembershipThroughOrganizationResources() {
        Fixture fixture = new Fixture();
        fixture.authenticate(OWNER_ID, "owner@example.com");
        OrganizationHttpResponse organization = fixture.createOrganization("invite-integration");

        Response inviteResponse = fixture.organizationResource.inviteMember(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateInvitationHttpRequest("member@example.com", "developer"));
        InvitationHttpResponse invitation = responseBody(inviteResponse, InvitationHttpResponse.class);
        fixture.authenticate(INVITED_ID, "member@example.com");

        Response acceptResponse = fixture.organizationResource.acceptInvitation(
                "Bearer test-token",
                null,
                organization.id(),
                invitation.id(),
                new AcceptInvitationHttpRequest(invitation.acceptanceToken()));

        assertEquals(200, acceptResponse.getStatus());
        MembershipHttpResponse membership = responseBody(acceptResponse, MembershipHttpResponse.class);
        assertEquals(INVITED_ID, membership.supabaseUserId());
        assertEquals("developer", membership.role());
        assertEquals("active", membership.status());
    }

    @Test
    void authorizationResourceDeniesViewerAdminAction() {
        Fixture fixture = new Fixture();
        fixture.authenticate(OWNER_ID, "owner@example.com");
        OrganizationHttpResponse organization = fixture.createOrganization("authz-integration");
        fixture.organizationResource.addMembership(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateMembershipHttpRequest(VIEWER_ID, "viewer", "active"));
        fixture.authenticate(VIEWER_ID, "viewer@example.com");

        Response response = fixture.authorizationResource.checkAuthorization(
                "Bearer test-token",
                null,
                new AuthorizationCheckHttpRequest(organization.id(), "org.members.invite"));

        assertEquals(200, response.getStatus());
        AuthorizationDecisionHttpResponse decision = responseBody(response, AuthorizationDecisionHttpResponse.class);
        assertFalse(decision.allowed());
        assertEquals("forbidden", decision.code());
    }

    @Test
    void ownerRegistersRepositoryThroughOrganizationResource() {
        Fixture fixture = new Fixture();
        fixture.authenticate(OWNER_ID, "owner@example.com");
        OrganizationHttpResponse organization = fixture.createOrganization("repository-integration");

        Response response = fixture.organizationResource.registerRepository(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateRepositoryHttpRequest(
                        "github",
                        "123456789",
                        "acme/payments-api",
                        "main",
                        "private"));

        assertEquals(201, response.getStatus());
        RepositoryHttpResponse repository = responseBody(response, RepositoryHttpResponse.class);
        assertEquals(organization.id(), repository.organizationId());
        assertEquals("github", repository.provider());
        assertEquals("acme/payments-api", repository.fullName());
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        return assertInstanceOf(type, envelope.data());
    }

    private static final class Fixture {
        private final DynamicUserPrincipalResolver resolver = new DynamicUserPrincipalResolver();
        private final OrganizationApplicationService service = new OrganizationApplicationService(
                new InMemoryOrganizationRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        private final OrganizationResource organizationResource = new OrganizationResource(service, resolver);
        private final AuthorizationResource authorizationResource = new AuthorizationResource(service, resolver);

        private void authenticate(UUID userId, String email) {
            resolver.user = new AuthenticatedUser(userId, email);
        }

        private OrganizationHttpResponse createOrganization(String slug) {
            Response response = organizationResource.createOrganization(
                    "Bearer test-token",
                    null,
                    new CreateOrganizationHttpRequest("Acme Engineering", slug, "team"));
            assertEquals(201, response.getStatus());
            return responseBody(response, OrganizationHttpResponse.class);
        }
    }

    private static final class DynamicUserPrincipalResolver implements UserPrincipalResolver {
        private AuthenticatedUser user;

        @Override
        public AuthenticatedUser resolve(UserAuthContext context) {
            return user;
        }
    }
}
