package dev.vericov.organization.bdd.steps;

import dev.vericov.organization.api.AcceptInvitationHttpRequest;
import dev.vericov.organization.api.ApiError;
import dev.vericov.organization.api.ApiResponse;
import dev.vericov.organization.api.AuthorizationCheckHttpRequest;
import dev.vericov.organization.api.AuthorizationDecisionHttpResponse;
import dev.vericov.organization.api.AuthorizationResource;
import dev.vericov.organization.api.BadgeTokenHttpResponse;
import dev.vericov.organization.api.CreateInvitationHttpRequest;
import dev.vericov.organization.api.CreateMembershipHttpRequest;
import dev.vericov.organization.api.CreateOrganizationHttpRequest;
import dev.vericov.organization.api.CreateRepositoryApiKeyHttpRequest;
import dev.vericov.organization.api.CreateRepositoryHttpRequest;
import dev.vericov.organization.api.CoverageBadgeHttpResponse;
import dev.vericov.organization.api.InvitationHttpResponse;
import dev.vericov.organization.api.MembershipHttpResponse;
import dev.vericov.organization.api.OrganizationHttpResponse;
import dev.vericov.organization.api.OrganizationResource;
import dev.vericov.organization.api.RepositoryApiKeyHttpResponse;
import dev.vericov.organization.api.CoverageLineHitMapHttpResponse;
import dev.vericov.organization.api.CoverageReportHttpResponse;
import dev.vericov.organization.api.CoverageTrendHttpResponse;
import dev.vericov.organization.api.GateEvaluationHttpResponse;
import dev.vericov.organization.api.PullRequestCoverageReportHttpResponse;
import dev.vericov.organization.api.RepositoryBadgeSettingsHttpRequest;
import dev.vericov.organization.api.RepositoryHttpResponse;
import dev.vericov.organization.api.RepositoryControlPlaneResource;
import dev.vericov.organization.api.CreateCoverageDebtHttpRequest;
import dev.vericov.organization.api.RepositoryGateHttpRequest;
import dev.vericov.organization.api.RepositoryGateHttpResponse;
import dev.vericov.organization.api.UpdateCoverageDebtHttpRequest;
import dev.vericov.organization.api.CoverageDebtHttpResponse;
import dev.vericov.organization.application.CoverageFileSummaryDetails;
import dev.vericov.organization.application.CoverageLineHitMapDetails;
import dev.vericov.organization.application.CoverageMetricDetails;
import dev.vericov.organization.application.CoverageReportSummary;
import dev.vericov.organization.application.DiffCoverageFileDetails;
import dev.vericov.organization.application.DiffCoverageLineDetails;
import dev.vericov.organization.application.GateEvaluationDetails;
import dev.vericov.organization.application.InMemoryOrganizationRepository;
import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.PullRequestDiffCoverageDetails;
import dev.vericov.organization.application.port.UserPrincipalResolver;
import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrganizationSteps {
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    private final DynamicUserPrincipalResolver resolver = new DynamicUserPrincipalResolver();
    private final InMemoryOrganizationRepository repositoryStore = new InMemoryOrganizationRepository();
    private final OrganizationApplicationService service = new OrganizationApplicationService(
            repositoryStore,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final OrganizationResource organizationResource = new OrganizationResource(service, resolver);
    private final AuthorizationResource authorizationResource = new AuthorizationResource(service, resolver);
    private final RepositoryControlPlaneResource repositoryControlPlaneResource = new RepositoryControlPlaneResource(service, resolver);
    private CoverageDebtHttpResponse coverageDebt;

    private OrganizationHttpResponse organization;
    private InvitationHttpResponse invitation;
    private MembershipHttpResponse membership;
    private RepositoryHttpResponse repository;
    private RepositoryApiKeyHttpResponse repositoryApiKey;
    private AuthorizationDecisionHttpResponse authorizationDecision;
    private CoverageTrendHttpResponse coverageTrend;
    private CoverageReportHttpResponse commitCoverageReport;
    private PullRequestCoverageReportHttpResponse pullRequestCoverageReport;
    private CoverageLineHitMapHttpResponse coverageLineHits;
    private BadgeTokenHttpResponse badgeToken;
    private CoverageBadgeHttpResponse coverageBadge;
    private String coverageBadgeSvg;
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

    @When("the current user creates repository API key {string} for branch {string}")
    public void currentUserCreatesRepositoryApiKeyForBranch(String name, String branch) {
        latestResponse = repositoryControlPlaneResource.createRepositoryApiKey(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new CreateRepositoryApiKeyHttpRequest(
                        name,
                        List.of("uploads:create", "uploads:read"),
                        List.of(branch),
                        NOW.plusSeconds(3600)));
        if (latestResponse.getStatus() == 201) {
            repositoryApiKey = responseBody(latestResponse, RepositoryApiKeyHttpResponse.class);
        }
    }

    @Then("a plaintext repository API key is returned once")
    public void plaintextRepositoryApiKeyIsReturnedOnce() {
        assertEquals(201, latestResponse.getStatus());
        assertEquals(repository.id(), repositoryApiKey.repositoryId());
        assertEquals("CI upload", repositoryApiKey.name());
        assertEquals(List.of("uploads:create", "uploads:read"), repositoryApiKey.scopes());
        assertEquals(List.of("main"), repositoryApiKey.branchAllowPatterns());
        assertFalse(repositoryApiKey.apiKey().isBlank());
        assertTrue(repositoryApiKey.apiKey().startsWith("vc_repo_"));
        assertEquals(
                repositoryApiKey.apiKey().substring(0, repositoryApiKey.keyPrefix().length()),
                repositoryApiKey.keyPrefix());
    }

    @When("the current user lists repository API keys")
    public void currentUserListsRepositoryApiKeys() {
        latestResponse = repositoryControlPlaneResource.listRepositoryApiKeys(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id());
    }

    @Then("the list contains the repository API key without plaintext secret")
    public void listContainsRepositoryApiKeyWithoutPlaintextSecret() {
        assertEquals(200, latestResponse.getStatus());
        List<?> keys = responseBody(latestResponse, List.class);
        RepositoryApiKeyHttpResponse listed = keys.stream()
                .filter(RepositoryApiKeyHttpResponse.class::isInstance)
                .map(RepositoryApiKeyHttpResponse.class::cast)
                .filter(key -> key.id().equals(repositoryApiKey.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(repositoryApiKey.keyPrefix(), listed.keyPrefix());
        assertNull(listed.apiKey());
    }

    @When("the current user revokes the repository API key")
    public void currentUserRevokesRepositoryApiKey() {
        latestResponse = repositoryControlPlaneResource.revokeRepositoryApiKey(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                repositoryApiKey.id());
        if (latestResponse.getStatus() == 200) {
            repositoryApiKey = responseBody(latestResponse, RepositoryApiKeyHttpResponse.class);
        }
    }

    @Then("the repository API key is revoked")
    public void repositoryApiKeyIsRevoked() {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(repository.id(), repositoryApiKey.repositoryId());
        assertNull(repositoryApiKey.apiKey());
        assertEquals(NOW, repositoryApiKey.revokedAt());
    }

    @When("the current user creates coverage debt for file {string} with risk {string} and reason {string} and owner {string}")
    public void createCoverageDebt(String filePath, String risk, String reason, String owner) {
        CreateCoverageDebtHttpRequest request = new CreateCoverageDebtHttpRequest(
                null,
                null,
                "commit-sha-bdd",
                null,
                "file",
                filePath,
                null,
                null,
                null,
                risk,
                reason,
                owner,
                Instant.now().plusSeconds(86400),
                null,
                java.util.Map.of()
        );
        latestResponse = repositoryControlPlaneResource.createCoverageDebt(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                request
        );
        if (latestResponse.getStatus() == 201) {
            coverageDebt = responseBody(latestResponse, CoverageDebtHttpResponse.class);
        }
    }

    @Then("the coverage debt is successfully created with status {string}")
    public void coverageDebtCreatedWithStatus(String status) {
        assertEquals(201, latestResponse.getStatus());
        assertEquals(status, coverageDebt.status());
    }

    @Then("the current user can retrieve details for the created coverage debt")
    public void canRetrieveCoverageDebtDetails() {
        Response response = repositoryControlPlaneResource.getCoverageDebt(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                coverageDebt.id()
        );
        assertEquals(200, response.getStatus());
        CoverageDebtHttpResponse retrieved = responseBody(response, CoverageDebtHttpResponse.class);
        assertEquals(coverageDebt.id(), retrieved.id());
    }

    @When("the current user lists coverage debts with status {string}")
    public void listCoverageDebts(String status) {
        latestResponse = repositoryControlPlaneResource.listCoverageDebts(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                status,
                null,
                null,
                null,
                null,
                true,
                null,
                100
        );
    }

    @Then("the list contains the created coverage debt")
    public void listContainsCreatedDebt() {
        assertEquals(200, latestResponse.getStatus());
        List<?> debts = responseBody(latestResponse, List.class);
        boolean found = debts.stream()
                .filter(d -> d instanceof CoverageDebtHttpResponse)
                .map(d -> (CoverageDebtHttpResponse) d)
                .anyMatch(d -> d.id().equals(coverageDebt.id()));
        assertTrue(found);
    }

    @When("the current user updates the coverage debt owner to {string} and reason to {string}")
    public void updateCoverageDebt(String newOwner, String newReason) {
        UpdateCoverageDebtHttpRequest request = new UpdateCoverageDebtHttpRequest(
                newOwner,
                null,
                newReason,
                null,
                null,
                null
        );
        latestResponse = repositoryControlPlaneResource.updateCoverageDebt(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                coverageDebt.id(),
                request
        );
        if (latestResponse.getStatus() == 200) {
            coverageDebt = responseBody(latestResponse, CoverageDebtHttpResponse.class);
        }
    }

    @Then("the coverage debt details reflect the updated owner {string} and reason {string}")
    public void coverageDebtReflectsUpdates(String owner, String reason) {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(owner, coverageDebt.owner());
        assertEquals(reason, coverageDebt.reason());
    }

    @When("the current user resolves the coverage debt")
    public void resolveCoverageDebt() {
        latestResponse = repositoryControlPlaneResource.resolveCoverageDebt(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                coverageDebt.id()
        );
        if (latestResponse.getStatus() == 200) {
            coverageDebt = responseBody(latestResponse, CoverageDebtHttpResponse.class);
        }
    }

    @Then("the coverage debt status is {string}")
    public void coverageDebtStatusIs(String status) {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(status, coverageDebt.status());
    }

    @When("the current user revokes the coverage debt")
    public void revokeCoverageDebt() {
        latestResponse = repositoryControlPlaneResource.revokeCoverageDebt(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                coverageDebt.id()
        );
        if (latestResponse.getStatus() == 200) {
            coverageDebt = responseBody(latestResponse, CoverageDebtHttpResponse.class);
        }
    }

    @When("the user {string} attempts to create coverage debt for file {string}")
    public void attemptCreateCoverageDebtViewer(String email, String filePath) {
        authenticate(email);
        CreateCoverageDebtHttpRequest request = new CreateCoverageDebtHttpRequest(
                null,
                null,
                "commit-sha-bdd",
                null,
                "file",
                filePath,
                null,
                null,
                null,
                "high",
                "viewer attempting bypass",
                "viewer",
                Instant.now().plusSeconds(86400),
                null,
                java.util.Map.of()
        );
        latestResponse = repositoryControlPlaneResource.createCoverageDebt(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                request
        );
    }

    @Given("a coverage report {string} on branch {string} created at {string} exists with line {int}\\/{int}, branch {int}\\/{int}, function {int}\\/{int}, and statement {int}\\/{int}")
    public void coverageReportExistsWithMetrics(
            String commitSha,
            String branch,
            String createdAt,
            int lineCovered,
            int lineTotal,
            int branchCovered,
            int branchTotal,
            int functionCovered,
            int functionTotal,
            int statementCovered,
            int statementTotal) {
        saveCoverageReport(
                commitSha,
                branch,
                null,
                Instant.parse(createdAt),
                lineCovered,
                lineTotal,
                branchCovered,
                branchTotal,
                functionCovered,
                functionTotal,
                statementCovered,
                statementTotal);
    }

    @Given("a pull request coverage report exists for commit {string} on branch {string}")
    public void pullRequestCoverageReportExists(String commitSha, String branch) {
        CoverageReportSummary report = saveCoverageReport(
                commitSha,
                branch,
                42,
                NOW,
                33,
                40,
                8,
                10,
                5,
                5,
                20,
                25);
        repositoryStore.saveCoverageFileSummary(new CoverageFileSummaryDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                report.id(),
                repository.id(),
                commitSha,
                "src/App.java",
                10,
                12,
                4,
                5,
                2,
                2,
                9,
                10,
                NOW));
        repositoryStore.savePullRequestDiffCoverage(new PullRequestDiffCoverageDetails(
                UUID.randomUUID(),
                report.id(),
                "abc122",
                commitSha,
                "complete",
                CoverageMetricDetails.of(1, 2),
                1,
                1,
                List.of(new DiffCoverageFileDetails(
                        "src/App.java",
                        null,
                        "modified",
                        CoverageMetricDetails.of(1, 2),
                        1,
                        1,
                        List.of(
                                new DiffCoverageLineDetails(
                                        "src/App.java",
                                        null,
                                        null,
                                        14,
                                        "added",
                                        true,
                                        null,
                                        0L,
                                        true,
                                        false),
                                new DiffCoverageLineDetails(
                                        "src/App.java",
                                        null,
                                        20,
                                        20,
                                        "context",
                                        true,
                                        3L,
                                        0L,
                                        false,
                                        true)))),
                NOW,
                NOW));
        repositoryStore.saveCoverageLineHits(new CoverageLineHitMapDetails(
                repository.id(),
                report.id(),
                commitSha,
                Map.of("src/App.java", Map.of(12, 4L, 14, 0L, 20, 0L))));
    }

    @When("the current user requests {string} coverage trends for branch {string} from {string} to {string}")
    public void currentUserRequestsCoverageTrends(String metric, String branch, String from, String to) {
        latestResponse = repositoryControlPlaneResource.listCoverageTrends(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                branch,
                metric,
                from,
                to,
                100);
        if (latestResponse.getStatus() == 200) {
            coverageTrend = responseBody(latestResponse, CoverageTrendHttpResponse.class);
        }
    }

    @Then("the coverage trend contains only commit {string} for metric {string} at {string} percent")
    public void coverageTrendContainsOnlyCommitForMetricAtPercent(String commitSha, String metric, String percent) {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(1, coverageTrend.points().size());
        CoverageTrendHttpResponse.Point point = coverageTrend.points().getFirst();
        assertEquals(commitSha, point.commitSha());
        assertEquals(metric, point.metric());
        assertEquals(0, new BigDecimal(percent).compareTo(point.percent()));
    }

    @When("the current user requests commit coverage report for {string} including files")
    public void currentUserRequestsCommitCoverageReportIncludingFiles(String commitSha) {
        latestResponse = repositoryControlPlaneResource.getCommitCoverageReport(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                commitSha,
                true,
                100);
        if (latestResponse.getStatus() == 200) {
            commitCoverageReport = responseBody(latestResponse, CoverageReportHttpResponse.class);
        }
    }

    @Then("the commit coverage report exposes line {string}, branch {string}, function {string}, and statement {string} percent")
    public void commitCoverageReportExposesAllMetricPercents(
            String line,
            String branch,
            String function,
            String statement) {
        assertEquals(200, latestResponse.getStatus());
        assertPercent(line, commitCoverageReport.line().percent());
        assertPercent(branch, commitCoverageReport.branchCoverage().percent());
        assertPercent(function, commitCoverageReport.function().percent());
        assertPercent(statement, commitCoverageReport.statement().percent());
    }

    @Then("the commit coverage report contains file {string}")
    public void commitCoverageReportContainsFile(String filePath) {
        assertTrue(commitCoverageReport.files().stream().anyMatch(file -> file.filePath().equals(filePath)));
    }

    @When("the current user requests pull request {int} coverage report including diff lines")
    public void currentUserRequestsPullRequestCoverageReportIncludingDiffLines(int pullRequestNumber) {
        latestResponse = repositoryControlPlaneResource.getPullRequestCoverageReport(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                pullRequestNumber,
                true,
                true,
                100);
        if (latestResponse.getStatus() == 200) {
            pullRequestCoverageReport = responseBody(latestResponse, PullRequestCoverageReportHttpResponse.class);
        }
    }

    @Then("the pull request coverage report includes {string} diff coverage with {int} diff lines")
    public void pullRequestCoverageReportIncludesDiffCoverageWithLines(String status, int lineCount) {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(status, pullRequestCoverageReport.diff().status());
        assertEquals(lineCount, pullRequestCoverageReport.diff().files().getFirst().lines().size());
    }

    @When("the current user requests coverage line hits for commit {string} file {string}")
    public void currentUserRequestsCoverageLineHits(String commitSha, String filePath) {
        latestResponse = repositoryControlPlaneResource.getCoverageLineHits(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                commitSha,
                filePath);
        if (latestResponse.getStatus() == 200) {
            coverageLineHits = responseBody(latestResponse, CoverageLineHitMapHttpResponse.class);
        }
    }

    @Then("line hits include file {string} line {int} with {int} hits")
    public void lineHitsIncludeFileLineWithHits(String filePath, int lineNumber, int hits) {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(hits, coverageLineHits.files().get(filePath).get(lineNumber));
    }

    @When("the current user enables coverage badge metric {string} on branch {string}")
    public void currentUserEnablesCoverageBadgeMetricOnBranch(String metric, String branch) {
        latestResponse = repositoryControlPlaneResource.upsertRepositoryBadgeSettings(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new RepositoryBadgeSettingsHttpRequest(
                        true,
                        branch,
                        metric,
                        "coverage",
                        Map.of()));
        assertEquals(200, latestResponse.getStatus());
    }

    @When("the current user rotates the coverage badge token")
    public void currentUserRotatesTheCoverageBadgeToken() {
        latestResponse = repositoryControlPlaneResource.rotateRepositoryBadgeToken(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id());
        if (latestResponse.getStatus() == 200) {
            badgeToken = responseBody(latestResponse, BadgeTokenHttpResponse.class);
        }
    }

    @When("an unauthenticated client requests coverage badge JSON for metric {string} with the token")
    public void unauthenticatedClientRequestsCoverageBadgeJsonWithToken(String metric) {
        latestResponse = repositoryControlPlaneResource.getCoverageBadgeJson(
                null,
                null,
                organization.id(),
                repository.id(),
                badgeToken.token(),
                null,
                metric);
        if (latestResponse.getStatus() == 200) {
            coverageBadge = responseBody(latestResponse, CoverageBadgeHttpResponse.class);
        }
    }

    @When("an unauthenticated client requests coverage badge SVG for metric {string} with the token")
    public void unauthenticatedClientRequestsCoverageBadgeSvgWithToken(String metric) {
        latestResponse = repositoryControlPlaneResource.getCoverageBadgeSvg(
                null,
                null,
                organization.id(),
                repository.id(),
                badgeToken.token(),
                null,
                metric,
                null);
        if (latestResponse.getStatus() == 200) {
            coverageBadgeSvg = assertInstanceOf(String.class, latestResponse.getEntity());
        }
    }

    @When("the current user requests authenticated coverage badge JSON for metric {string}")
    public void currentUserRequestsAuthenticatedCoverageBadgeJson(String metric) {
        latestResponse = repositoryControlPlaneResource.getCoverageBadgeJson(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                null,
                null,
                metric);
        if (latestResponse.getStatus() == 200) {
            coverageBadge = responseBody(latestResponse, CoverageBadgeHttpResponse.class);
        }
    }

    @Then("the coverage badge response has message {string} and color {string}")
    public void coverageBadgeResponseHasMessageAndColor(String message, String color) {
        assertEquals(200, latestResponse.getStatus());
        assertEquals(message, coverageBadge.message());
        assertEquals(color, coverageBadge.color());
    }

    @Then("the coverage badge SVG contains {string}")
    public void coverageBadgeSvgContains(String expected) {
        assertEquals(200, latestResponse.getStatus());
        assertTrue(coverageBadgeSvg.contains(expected));
    }

    @When("the current user configures project coverage gates for all coverage metrics")
    public void currentUserConfiguresProjectCoverageGatesForAllCoverageMetrics() {
        latestResponse = repositoryControlPlaneResource.replaceRepositoryGates(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                List.of(
                        gateRequest("Project line coverage", "line", "80"),
                        gateRequest("Project branch coverage", "branch", "80"),
                        gateRequest("Project function coverage", "function", "90"),
                        gateRequest("Project statement coverage", "statement", "80")));
    }

    @Then("repository gates include metrics line, branch, function, and statement")
    public void repositoryGatesIncludeAllCoverageMetrics() {
        assertEquals(200, latestResponse.getStatus());
        List<?> gates = responseBody(latestResponse, List.class);
        assertTrue(gates.stream().map(RepositoryGateHttpResponse.class::cast).anyMatch(gate -> gate.metric().equals("line")));
        assertTrue(gates.stream().map(RepositoryGateHttpResponse.class::cast).anyMatch(gate -> gate.metric().equals("branch")));
        assertTrue(gates.stream().map(RepositoryGateHttpResponse.class::cast).anyMatch(gate -> gate.metric().equals("function")));
        assertTrue(gates.stream().map(RepositoryGateHttpResponse.class::cast).anyMatch(gate -> gate.metric().equals("statement")));
    }

    @Given("gate evaluations exist for passed branch and failed line coverage")
    public void gateEvaluationsExistForPassedBranchAndFailedLineCoverage() {
        CoverageReportSummary report = saveCoverageReport(
                "abc123",
                "main",
                42,
                NOW,
                33,
                40,
                8,
                10,
                5,
                5,
                20,
                25);
        repositoryStore.saveGateEvaluation(new GateEvaluationDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                organization.id(),
                repository.id(),
                report.id(),
                report.commitSha(),
                "main",
                42,
                "Project line coverage",
                "project_coverage",
                "line",
                new BigDecimal("85"),
                new BigDecimal("82.5"),
                "failed",
                true,
                Map.of("summary", "below threshold"),
                NOW));
        repositoryStore.saveGateEvaluation(new GateEvaluationDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                organization.id(),
                repository.id(),
                report.id(),
                report.commitSha(),
                "main",
                42,
                "Project branch coverage",
                "project_coverage",
                "branch",
                new BigDecimal("75"),
                new BigDecimal("80"),
                "passed",
                true,
                Map.of("summary", "meets threshold"),
                NOW));
    }

    @When("the current user lists gate evaluations with status {string}")
    public void currentUserListsGateEvaluationsWithStatus(String status) {
        latestResponse = repositoryControlPlaneResource.listGateEvaluations(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "main",
                status,
                100);
    }

    @Then("gate evaluations include {string} for metric {string} with status {string}")
    public void gateEvaluationsIncludeForMetricWithStatus(String gateName, String metric, String status) {
        assertEquals(200, latestResponse.getStatus());
        List<?> evaluations = responseBody(latestResponse, List.class);
        assertTrue(evaluations.stream()
                .map(GateEvaluationHttpResponse.class::cast)
                .anyMatch(evaluation -> evaluation.gateName().equals(gateName)
                        && evaluation.metric().equals(metric)
                        && evaluation.status().equals(status)));
    }

    private void authenticate(String email) {
        currentEmail = email;
        resolver.user = new AuthenticatedUser(userId(email), email);
    }

    private CoverageReportSummary saveCoverageReport(
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            Instant createdAt,
            int lineCovered,
            int lineTotal,
            int branchCovered,
            int branchTotal,
            int functionCovered,
            int functionTotal,
            int statementCovered,
            int statementTotal) {
        return repositoryStore.saveCoverageReport(new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                commitSha,
                branch,
                pullRequestNumber,
                lineCovered,
                lineTotal,
                branchCovered,
                branchTotal,
                functionCovered,
                functionTotal,
                statementCovered,
                statementTotal,
                createdAt,
                createdAt));
    }

    private static void assertPercent(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static RepositoryGateHttpRequest gateRequest(String name, String metric, String threshold) {
        return new RepositoryGateHttpRequest(
                name,
                "project_coverage",
                metric,
                new BigDecimal(threshold),
                null,
                true,
                Map.of(),
                "active");
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
