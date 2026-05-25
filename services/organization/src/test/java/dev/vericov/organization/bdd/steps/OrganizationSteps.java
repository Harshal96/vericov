package dev.vericov.organization.bdd.steps;

import dev.vericov.organization.api.AcceptInvitationHttpRequest;
import dev.vericov.organization.api.ApiError;
import dev.vericov.organization.api.ApiResponse;
import dev.vericov.organization.api.AuthorizationCheckHttpRequest;
import dev.vericov.organization.api.AuthorizationDecisionHttpResponse;
import dev.vericov.organization.api.AuthorizationResource;
import dev.vericov.organization.api.CreateInvitationHttpRequest;
import dev.vericov.organization.api.CreateMembershipHttpRequest;
import dev.vericov.organization.api.CreateOrganizationHttpRequest;
import dev.vericov.organization.api.CreateRepositoryHttpRequest;
import dev.vericov.organization.api.InvitationHttpResponse;
import dev.vericov.organization.api.MembershipHttpResponse;
import dev.vericov.organization.api.OrganizationHttpResponse;
import dev.vericov.organization.api.OrganizationResource;
import dev.vericov.organization.api.RepositoryHttpResponse;
import dev.vericov.organization.application.InMemoryOrganizationRepository;
import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.port.UserPrincipalResolver;
import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class OrganizationSteps {
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    private final DynamicUserPrincipalResolver resolver = new DynamicUserPrincipalResolver();
    private final OrganizationApplicationService service = new OrganizationApplicationService(
            new InMemoryOrganizationRepository(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final OrganizationResource organizationResource = new OrganizationResource(service, resolver);
    private final AuthorizationResource authorizationResource = new AuthorizationResource(service, resolver);

    private OrganizationHttpResponse organization;
    private InvitationHttpResponse invitation;
    private MembershipHttpResponse membership;
    private RepositoryHttpResponse repository;
    private AuthorizationDecisionHttpResponse authorizationDecision;
    private Response latestResponse;
    private String currentEmail;

    @Given("authenticated user {string}")
    public void authenticatedUser(String email) {
        authenticate(email);
    }

    @Given("the current user created organization {string} with slug {string}")
    public void currentUserCreatedOrganizationWithSlug(String name, String slug) {
        userCreatesOrganizationWithSlugAndPlan(name, slug, "team");
        organizationApiCreatesActiveOrganization();
    }

    @Given("the current user adds user {string} as {string}")
    public void currentUserAddsUserAs(String email, String role) {
        latestResponse = organizationResource.addMembership(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateMembershipHttpRequest(userId(email), role, "active"));
        assertEquals(201, latestResponse.getStatus());
        membership = responseBody(latestResponse, MembershipHttpResponse.class);
        assertEquals(role, membership.role());
    }

    @When("the user creates organization {string} with slug {string} and plan {string}")
    public void userCreatesOrganizationWithSlugAndPlan(String name, String slug, String plan) {
        latestResponse = organizationResource.createOrganization(
                "Bearer test-token",
                null,
                new CreateOrganizationHttpRequest(name, slug, plan));
        if (latestResponse.getStatus() == 201) {
            organization = responseBody(latestResponse, OrganizationHttpResponse.class);
        }
    }

    @When("user {string} invites {string} as {string}")
    public void userInvitesAs(String inviterEmail, String invitedEmail, String role) {
        authenticate(inviterEmail);
        inviteCurrentUserInvitesAs(invitedEmail, role);
    }

    @When("the current user invites {string} as {string}")
    public void inviteCurrentUserInvitesAs(String email, String role) {
        latestResponse = organizationResource.inviteMember(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateInvitationHttpRequest(email, role));
        if (latestResponse.getStatus() == 201) {
            invitation = responseBody(latestResponse, InvitationHttpResponse.class);
        }
    }

    @When("user {string} accepts the invitation")
    public void userAcceptsTheInvitation(String email) {
        authenticate(email);
        latestResponse = organizationResource.acceptInvitation(
                "Bearer test-token",
                null,
                organization.id(),
                invitation.id(),
                new AcceptInvitationHttpRequest(invitation.acceptanceToken()));
        if (latestResponse.getStatus() == 200) {
            membership = responseBody(latestResponse, MembershipHttpResponse.class);
        }
    }

    @When("user {string} checks authorization for {string}")
    public void userChecksAuthorizationFor(String email, String action) {
        authenticate(email);
        latestResponse = authorizationResource.checkAuthorization(
                "Bearer test-token",
                null,
                new AuthorizationCheckHttpRequest(organization.id(), action));
        authorizationDecision = responseBody(latestResponse, AuthorizationDecisionHttpResponse.class);
    }

    @When("the current user registers GitHub repository {string}")
    public void currentUserRegistersGitHubRepository(String fullName) {
        latestResponse = organizationResource.registerRepository(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateRepositoryHttpRequest("github", "123456789", fullName, "main", "private"));
        if (latestResponse.getStatus() == 201) {
            repository = responseBody(latestResponse, RepositoryHttpResponse.class);
        }
    }

    @Then("the organization API creates an active organization")
    public void organizationApiCreatesActiveOrganization() {
        assertEquals(201, latestResponse.getStatus());
        assertEquals("active", organization.status());
        assertEquals(NOW, organization.createdAt());
        assertEquals(NOW, organization.updatedAt());
        assertEquals("/api/v1/orgs/" + organization.id(), latestResponse.getLocation().toString());
    }

    @Then("the current user is an active owner member")
    public void currentUserIsActiveOwnerMember() {
        Response response = organizationResource.listMemberships("Bearer test-token", null, organization.id());
        assertEquals(200, response.getStatus());
        List<?> memberships = responseBody(response, List.class);
        assertEquals(1, memberships.size());
        MembershipHttpResponse owner = assertInstanceOf(MembershipHttpResponse.class, memberships.getFirst());
        assertEquals(userId(currentEmail), owner.supabaseUserId());
        assertEquals("owner", owner.role());
        assertEquals("active", owner.status());
    }

    @Then("the organization API rejects the request with status {int} and code {string}")
    public void organizationApiRejectsRequestWithStatusAndCode(int status, String code) {
        assertEquals(status, latestResponse.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, latestResponse.getEntity());
        assertEquals(code, error.error().code());
        assertFalse(error.error().message().isBlank());
    }

    @Then("the invited user has an active {string} membership")
    public void invitedUserHasActiveMembership(String role) {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(userId(currentEmail), membership.supabaseUserId());
        assertEquals(role, membership.role());
        assertEquals("active", membership.status());
    }

    @Then("authorization is denied with code {string}")
    public void authorizationIsDeniedWithCode(String code) {
        assertEquals(200, latestResponse.getStatus());
        assertFalse(authorizationDecision.allowed());
        assertEquals(code, authorizationDecision.code());
    }

    @Then("the repository API creates an active repository for the organization")
    public void repositoryApiCreatesActiveRepositoryForTheOrganization() {
        assertEquals(201, latestResponse.getStatus());
        assertEquals(organization.id(), repository.organizationId());
        assertEquals("github", repository.provider());
        assertEquals("123456789", repository.providerRepositoryId());
        assertEquals("active", repository.status());
        assertEquals(NOW, repository.createdAt());
        assertEquals(NOW, repository.updatedAt());
        assertEquals(
                "/api/v1/orgs/" + organization.id() + "/repositories/" + repository.id(),
                latestResponse.getLocation().toString());
    }

    private void authenticate(String email) {
        currentEmail = email;
        resolver.user = new AuthenticatedUser(userId(email), email);
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        return assertInstanceOf(type, envelope.data());
    }

    private static UUID userId(String email) {
        return UUID.nameUUIDFromBytes(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static final class DynamicUserPrincipalResolver implements UserPrincipalResolver {
        private AuthenticatedUser user;

        @Override
        public AuthenticatedUser resolve(UserAuthContext context) {
            return user;
        }
    }
}
