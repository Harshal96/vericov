package dev.vericov.organization.api;

import dev.vericov.organization.application.InMemoryOrganizationRepository;
import dev.vericov.organization.application.CoverageFileSummaryDetails;
import dev.vericov.organization.application.CoverageLineHitMapDetails;
import dev.vericov.organization.application.CoverageMetricDetails;
import dev.vericov.organization.application.CoverageReportSummary;
import dev.vericov.organization.application.DiffCoverageFileDetails;
import dev.vericov.organization.application.DiffCoverageLineDetails;
import dev.vericov.organization.application.GateEvaluationDetails;
import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.OrganizationException;
import dev.vericov.organization.application.PullRequestDiffCoverageDetails;
import dev.vericov.organization.application.TestRunDetails;
import dev.vericov.organization.application.port.UserPrincipalResolver;
import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationResourceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String MEMBER_EMAIL = "member@example.com";
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void createsOrganizationEnvelope() {
        var resource = resource();

        Response response = resource.createOrganization(
                "Bearer test-token",
                null,
                new CreateOrganizationHttpRequest("Acme Engineering", "acme", "team"));

        assertEquals(201, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        OrganizationHttpResponse body = assertInstanceOf(OrganizationHttpResponse.class, envelope.data());
        assertEquals("Acme Engineering", body.name());
        assertEquals("acme", body.slug());
        assertEquals("team", body.plan());
        assertEquals("active", body.status());
        assertEquals("/api/v1/orgs/" + body.id(), response.getLocation().toString());
    }

    @Test
    void listsVisibleOrganizationsEnvelope() {
        var resource = resource();
        resource.createOrganization(
                "Bearer test-token",
                null,
                new CreateOrganizationHttpRequest("Acme Engineering", "acme", "team"));

        Response response = resource.listOrganizations("Bearer test-token", null);

        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        assertEquals(1, assertInstanceOf(java.util.List.class, envelope.data()).size());
    }

    @Test
    void addsMembershipEnvelope() {
        var resource = resource();
        Response createResponse = resource.createOrganization(
                "Bearer test-token",
                null,
                new CreateOrganizationHttpRequest("Acme Engineering", "acme", "team"));
        OrganizationHttpResponse organization = assertInstanceOf(
                OrganizationHttpResponse.class,
                assertInstanceOf(ApiResponse.class, createResponse.getEntity()).data());

        Response response = resource.addMembership(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateMembershipHttpRequest(MEMBER_USER_ID, "developer", "active"));

        assertEquals(201, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        MembershipHttpResponse body = assertInstanceOf(MembershipHttpResponse.class, envelope.data());
        assertEquals(MEMBER_USER_ID, body.supabaseUserId());
        assertEquals("developer", body.role());
    }

    @Test
    void registersRepositoryEnvelope() {
        var resource = resource();
        OrganizationHttpResponse organization = createOrganization(resource);

        Response response = resource.registerRepository(
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
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        RepositoryHttpResponse body = assertInstanceOf(RepositoryHttpResponse.class, envelope.data());
        assertEquals(organization.id(), body.organizationId());
        assertEquals("github", body.provider());
        assertEquals("123456789", body.providerRepositoryId());
        assertEquals("acme/payments-api", body.fullName());
        assertEquals("main", body.defaultBranch());
        assertEquals("private", body.visibility());
        assertEquals("active", body.status());
        assertEquals(
                "/api/v1/orgs/" + organization.id() + "/repositories/" + body.id(),
                response.getLocation().toString());
    }

    @Test
    void listsRegisteredRepositoriesEnvelope() {
        var resource = resource();
        OrganizationHttpResponse organization = createOrganization(resource);
        resource.registerRepository(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateRepositoryHttpRequest(
                        "github",
                        "123456789",
                        "acme/payments-api",
                        "main",
                        "private"));

        Response response = resource.listRepositories("Bearer test-token", null, organization.id());

        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        assertEquals(1, assertInstanceOf(java.util.List.class, envelope.data()).size());
    }

    @Test
    void updatesRepositoryEnvelope() {
        var resource = resource();
        OrganizationHttpResponse organization = createOrganization(resource);
        Response registerResponse = resource.registerRepository(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateRepositoryHttpRequest(
                        "github",
                        "123456789",
                        "acme/payments-api",
                        "main",
                        "private"));
        RepositoryHttpResponse repository = assertInstanceOf(
                RepositoryHttpResponse.class,
                assertInstanceOf(ApiResponse.class, registerResponse.getEntity()).data());

        Response response = resource.updateRepository(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new UpdateRepositoryHttpRequest(
                        "acme/payments-service",
                        "release/2026",
                        "internal",
                        "active"));

        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        RepositoryHttpResponse body = assertInstanceOf(RepositoryHttpResponse.class, envelope.data());
        assertEquals("acme/payments-service", body.fullName());
        assertEquals("release/2026", body.defaultBranch());
        assertEquals("internal", body.visibility());
    }

    @Test
    void managesRepositoryControlsEnvelope() {
        OrganizationApplicationService service = service();
        var organizationResource = resourceWithUser(service, USER_ID, "owner@example.com");
        var controlPlaneResource = new RepositoryControlPlaneResource(service, fixedUser(USER_ID, "owner@example.com"));
        OrganizationHttpResponse organization = createOrganization(organizationResource);
        RepositoryHttpResponse repository = registerRepository(organizationResource, organization);

        Response defaultsResponse = controlPlaneResource.upsertPolicyDefaults(
                "Bearer test-token",
                null,
                organization.id(),
                new PolicyDefaultsHttpRequest(
                        Map.of("coverage", Map.of("project", new BigDecimal("80"))),
                        1));
        Response configResponse = controlPlaneResource.upsertRepositoryConfig(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new RepositoryConfigHttpRequest(
                        Map.of("coverage", Map.of("patch", new BigDecimal("75"))),
                        1));
        Response policyResponse = controlPlaneResource.createRepositoryPolicy(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new RepositoryPolicyHttpRequest(
                        "Coverage floor",
                        "Minimum project coverage",
                        "coverage",
                        "repository",
                        null,
                        Map.of("minimum", new BigDecimal("80")),
                        "active",
                        10));
        Response gatesResponse = controlPlaneResource.replaceRepositoryGates(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                List.of(new RepositoryGateHttpRequest(
                        "Project coverage",
                        "project_coverage",
                        "line",
                        new BigDecimal("80"),
                        null,
                        true,
                        Map.of("severity", "required"),
                        "active")));
        Response effectiveResponse = controlPlaneResource.getEffectiveRepositoryConfig(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id());

        assertEquals(200, defaultsResponse.getStatus());
        PolicyDefaultsHttpResponse defaults = responseBody(defaultsResponse, PolicyDefaultsHttpResponse.class);
        assertEquals(organization.id(), defaults.organizationId());
        assertEquals(1, defaults.schemaVersion());

        assertEquals(200, configResponse.getStatus());
        RepositoryConfigHttpResponse config = responseBody(configResponse, RepositoryConfigHttpResponse.class);
        assertEquals(repository.id(), config.repositoryId());
        assertEquals("valid", config.validationStatus());

        assertEquals(201, policyResponse.getStatus());
        RepositoryPolicyHttpResponse policy = responseBody(policyResponse, RepositoryPolicyHttpResponse.class);
        assertEquals("Coverage floor", policy.name());
        assertEquals(
                "/api/v1/orgs/" + organization.id() + "/repositories/" + repository.id() + "/policies/" + policy.id(),
                policyResponse.getLocation().toString());

        assertEquals(200, gatesResponse.getStatus());
        assertEquals(1, assertInstanceOf(List.class, responseEnvelope(gatesResponse).data()).size());

        assertEquals(200, effectiveResponse.getStatus());
        EffectiveRepositoryConfigHttpResponse effective = responseBody(
                effectiveResponse,
                EffectiveRepositoryConfigHttpResponse.class);
        assertEquals(1, effective.policies().size());
        assertEquals(1, effective.gates().size());
    }

    @Test
    void coverageBadgeEndpointsRenderSvgAndJson() {
        InMemoryOrganizationRepository repositoryStore = new InMemoryOrganizationRepository();
        OrganizationApplicationService service = new OrganizationApplicationService(
                repositoryStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var organizationResource = new OrganizationResource(service, fixedUser(USER_ID, "owner@example.com"));
        var controlPlaneResource = new RepositoryControlPlaneResource(service, fixedUser(USER_ID, "owner@example.com"));
        OrganizationHttpResponse organization = createOrganization(organizationResource);
        RepositoryHttpResponse repository = registerRepository(organizationResource, organization);
        repositoryStore.saveCoverageReport(new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "abc123",
                "main",
                33,
                40,
                8,
                10,
                5,
                5,
                20,
                25,
                NOW,
                NOW));
        controlPlaneResource.upsertRepositoryBadgeSettings(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new RepositoryBadgeSettingsHttpRequest(
                        true,
                        "main",
                        "line",
                        "coverage",
                        Map.of()));
        BadgeTokenHttpResponse token = responseBody(controlPlaneResource.rotateRepositoryBadgeToken(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id()), BadgeTokenHttpResponse.class);

        Response svgResponse = controlPlaneResource.getCoverageBadgeSvg(
                null,
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null,
                null);
        Response jsonResponse = controlPlaneResource.getCoverageBadgeJson(
                null,
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null);

        assertEquals(200, svgResponse.getStatus());
        assertEquals("image/svg+xml", svgResponse.getMediaType().toString());
        String svg = assertInstanceOf(String.class, svgResponse.getEntity());
        assertEquals(true, svg.contains("82.5%"));

        assertEquals(200, jsonResponse.getStatus());
        CoverageBadgeHttpResponse json = responseBody(jsonResponse, CoverageBadgeHttpResponse.class);
        assertEquals("coverage", json.label());
        assertEquals("82.5%", json.message());
        assertEquals("green", json.color());
    }

    @Test
    void coverageBadgeSvgSupportsStyleVariants() {
        InMemoryOrganizationRepository repositoryStore = new InMemoryOrganizationRepository();
        OrganizationApplicationService service = new OrganizationApplicationService(
                repositoryStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var organizationResource = new OrganizationResource(service, fixedUser(USER_ID, "owner@example.com"));
        var controlPlaneResource = new RepositoryControlPlaneResource(service, fixedUser(USER_ID, "owner@example.com"));
        OrganizationHttpResponse organization = createOrganization(organizationResource);
        RepositoryHttpResponse repository = registerRepository(organizationResource, organization);
        repositoryStore.saveCoverageReport(new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "abc123",
                "main",
                33,
                40,
                8,
                10,
                5,
                5,
                20,
                25,
                NOW,
                NOW));
        controlPlaneResource.upsertRepositoryBadgeSettings(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new RepositoryBadgeSettingsHttpRequest(
                        true,
                        "main",
                        "line",
                        "coverage",
                        Map.of()));
        BadgeTokenHttpResponse token = responseBody(controlPlaneResource.rotateRepositoryBadgeToken(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id()), BadgeTokenHttpResponse.class);

        Response squareResponse = controlPlaneResource.getCoverageBadgeSvg(
                null,
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null,
                "flat-square");
        Response plasticResponse = controlPlaneResource.getCoverageBadgeSvg(
                null,
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null,
                "plastic");
        Response forTheBadgeResponse = controlPlaneResource.getCoverageBadgeSvg(
                null,
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null,
                "for-the-badge");
        Response invalidResponse = controlPlaneResource.getCoverageBadgeSvg(
                null,
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null,
                "round");

        assertEquals(200, squareResponse.getStatus());
        assertTrue(assertInstanceOf(String.class, squareResponse.getEntity()).contains("rx=\"0\""));
        assertEquals(200, plasticResponse.getStatus());
        assertTrue(assertInstanceOf(String.class, plasticResponse.getEntity()).contains("id=\"plastic\""));
        assertEquals(200, forTheBadgeResponse.getStatus());
        assertTrue(assertInstanceOf(String.class, forTheBadgeResponse.getEntity()).contains("COVERAGE"));
        assertEquals(400, invalidResponse.getStatus());
    }

    @Test
    void reportTrendAndDashboardEndpointsReturnCoverageReadModels() {
        InMemoryOrganizationRepository repositoryStore = new InMemoryOrganizationRepository();
        OrganizationApplicationService service = new OrganizationApplicationService(
                repositoryStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var organizationResource = new OrganizationResource(service, fixedUser(USER_ID, "owner@example.com"));
        var controlPlaneResource = new RepositoryControlPlaneResource(service, fixedUser(USER_ID, "owner@example.com"));
        OrganizationHttpResponse organization = createOrganization(organizationResource);
        RepositoryHttpResponse repository = registerRepository(organizationResource, organization);
        UUID reportId = UUID.randomUUID();
        repositoryStore.saveCoverageReport(new CoverageReportSummary(
                reportId,
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "abc123",
                "main",
                42,
                33,
                40,
                8,
                10,
                5,
                5,
                20,
                25,
                NOW,
                NOW));
        repositoryStore.saveCoverageFileSummary(new CoverageFileSummaryDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                reportId,
                repository.id(),
                "abc123",
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
                reportId,
                "abc122",
                "abc123",
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
                reportId,
                "abc123",
                Map.of("src/App.java", Map.of(12, 4L, 14, 0L, 20, 0L))));
        repositoryStore.saveGateEvaluation(new GateEvaluationDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                organization.id(),
                repository.id(),
                reportId,
                "abc123",
                "main",
                42,
                "Project coverage",
                "project_coverage",
                "line",
                new BigDecimal("85"),
                new BigDecimal("82.5"),
                "failed",
                true,
                Map.of("summary", "below threshold"),
                NOW));
        repositoryStore.saveTestRun(new TestRunDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "abc123",
                "main",
                42,
                "unit",
                0,
                "failed",
                3,
                2,
                1,
                0,
                0,
                420L,
                NOW));

        Response commitResponse = controlPlaneResource.getCommitCoverageReport(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "abc123",
                true,
                100);
        Response prResponse = controlPlaneResource.getPullRequestCoverageReport(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                42,
                true,
                true,
                100);
        Response lineHitsResponse = controlPlaneResource.getCoverageLineHits(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "abc123",
                "src/App.java");
        Response testRunsResponse = controlPlaneResource.listCommitTestRuns(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "abc123",
                100);
        Response emptyTestRunsResponse = controlPlaneResource.listCommitTestRuns(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "def456",
                100);
        Response trendResponse = controlPlaneResource.listCoverageTrends(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "main",
                "line",
                100);
        Response gatesResponse = controlPlaneResource.listGateEvaluations(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "main",
                "failed",
                100);
        Response repositoryDashboardResponse = controlPlaneResource.getRepositoryDashboard(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "main");
        Response organizationDashboardResponse = controlPlaneResource.getOrganizationDashboard(
                "Bearer test-token",
                null,
                organization.id(),
                "main");
        Response repositoryDashboardsResponse = controlPlaneResource.listRepositoryDashboards(
                "Bearer test-token",
                null,
                organization.id(),
                "main");

        assertEquals(200, commitResponse.getStatus());
        CoverageReportHttpResponse commit = responseBody(commitResponse, CoverageReportHttpResponse.class);
        assertEquals("abc123", commit.commitSha());
        assertEquals(new BigDecimal("82.5"), commit.line().percent());
        assertEquals(1, commit.files().size());

        assertEquals(200, prResponse.getStatus());
        PullRequestCoverageReportHttpResponse pr = responseBody(
                prResponse,
                PullRequestCoverageReportHttpResponse.class);
        assertEquals(42, pr.pullRequestNumber());
        assertEquals("abc123", pr.headSha());
        assertEquals("complete", pr.diff().status());
        assertEquals(0, new BigDecimal("50").compareTo(pr.diff().patchLine().percent()));
        assertEquals(2, pr.diff().files().getFirst().lines().size());

        assertEquals(200, lineHitsResponse.getStatus());
        CoverageLineHitMapHttpResponse lineHits = responseBody(
                lineHitsResponse,
                CoverageLineHitMapHttpResponse.class);
        assertEquals(Map.of(12, 4L, 14, 0L, 20, 0L), lineHits.files().get("src/App.java"));

        assertEquals(200, testRunsResponse.getStatus());
        List<?> testRuns = assertInstanceOf(List.class, responseEnvelope(testRunsResponse).data());
        TestRunHttpResponse testRun = assertInstanceOf(TestRunHttpResponse.class, testRuns.getFirst());
        assertEquals("unit", testRun.suiteName());
        assertEquals("failed", testRun.status());
        assertEquals(3, testRun.totalCount());
        assertEquals(2, testRun.passedCount());
        assertEquals(420L, testRun.durationMs());

        assertEquals(200, emptyTestRunsResponse.getStatus());
        assertTrue(assertInstanceOf(List.class, responseEnvelope(emptyTestRunsResponse).data()).isEmpty());

        assertEquals(200, trendResponse.getStatus());
        CoverageTrendHttpResponse trend = responseBody(trendResponse, CoverageTrendHttpResponse.class);
        assertEquals(1, trend.points().size());

        assertEquals(200, gatesResponse.getStatus());
        assertEquals(1, assertInstanceOf(List.class, responseEnvelope(gatesResponse).data()).size());

        assertEquals(200, repositoryDashboardResponse.getStatus());
        RepositoryDashboardHttpResponse repositoryDashboard = responseBody(
                repositoryDashboardResponse,
                RepositoryDashboardHttpResponse.class);
        assertEquals("abc123", repositoryDashboard.latestCommitSha());

        assertEquals(200, organizationDashboardResponse.getStatus());
        OrganizationDashboardHttpResponse organizationDashboard = responseBody(
                organizationDashboardResponse,
                OrganizationDashboardHttpResponse.class);
        assertEquals(1, organizationDashboard.repositoryCount());

        assertEquals(200, repositoryDashboardsResponse.getStatus());
        assertEquals(1, assertInstanceOf(List.class, responseEnvelope(repositoryDashboardsResponse).data()).size());
    }

    @Test
    void testRunEndpointRejectsUsersWithoutRepositoryReadAccess() {
        OrganizationApplicationService service = service();
        var organizationResource = new OrganizationResource(service, fixedUser(USER_ID, "owner@example.com"));
        var unauthorizedControlPlaneResource =
                new RepositoryControlPlaneResource(service, fixedUser(MEMBER_USER_ID, MEMBER_EMAIL));
        OrganizationHttpResponse organization = createOrganization(organizationResource);
        RepositoryHttpResponse repository = registerRepository(organizationResource, organization);

        Response response = unauthorizedControlPlaneResource.listCommitTestRuns(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                "abc123",
                100);

        assertEquals(404, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("not_found", error.error().code());
    }

    @Test
    void invitesMemberEnvelope() {
        var resource = resource();
        OrganizationHttpResponse organization = createOrganization(resource);

        Response response = resource.inviteMember(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateInvitationHttpRequest(" Member@Example.com ", "developer"));

        assertEquals(201, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        InvitationHttpResponse body = assertInstanceOf(InvitationHttpResponse.class, envelope.data());
        assertEquals("member@example.com", body.email());
        assertEquals("developer", body.role());
        assertEquals("pending", body.status());
        assertEquals("/api/v1/orgs/" + organization.id() + "/invitations/" + body.id(), response.getLocation().toString());
    }

    @Test
    void updatesMembershipEnvelope() {
        var resource = resource();
        OrganizationHttpResponse organization = createOrganization(resource);
        Response addResponse = resource.addMembership(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateMembershipHttpRequest(MEMBER_USER_ID, "viewer", "active"));
        MembershipHttpResponse membership = assertInstanceOf(
                MembershipHttpResponse.class,
                assertInstanceOf(ApiResponse.class, addResponse.getEntity()).data());

        Response response = resource.updateMembership(
                "Bearer test-token",
                null,
                organization.id(),
                membership.id(),
                new UpdateMembershipHttpRequest("developer", "active"));

        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        MembershipHttpResponse body = assertInstanceOf(MembershipHttpResponse.class, envelope.data());
        assertEquals("developer", body.role());
        assertEquals("active", body.status());
    }

    @Test
    void acceptsInvitationEnvelope() {
        OrganizationApplicationService service = service();
        var ownerResource = resourceWithUser(service, USER_ID, "owner@example.com");
        OrganizationHttpResponse organization = createOrganization(ownerResource);
        Response inviteResponse = ownerResource.inviteMember(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateInvitationHttpRequest(MEMBER_EMAIL, "viewer"));
        InvitationHttpResponse invitation = assertInstanceOf(
                InvitationHttpResponse.class,
                assertInstanceOf(ApiResponse.class, inviteResponse.getEntity()).data());
        var memberResource = resourceWithUser(service, MEMBER_USER_ID, MEMBER_EMAIL);

        Response response = memberResource.acceptInvitation(
                "Bearer test-token",
                null,
                organization.id(),
                invitation.id(),
                new AcceptInvitationHttpRequest(invitation.acceptanceToken()));

        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        MembershipHttpResponse body = assertInstanceOf(MembershipHttpResponse.class, envelope.data());
        assertEquals(MEMBER_USER_ID, body.supabaseUserId());
        assertEquals("viewer", body.role());
    }

    @Test
    void returnsAuthorizationDecisionEnvelope() {
        OrganizationApplicationService service = service();
        var resource = resourceWithUser(service, USER_ID, "owner@example.com");
        var authorizationResource = new AuthorizationResource(service, fixedUser(USER_ID, "owner@example.com"));
        OrganizationHttpResponse organization = createOrganization(resource);

        Response response = authorizationResource.checkAuthorization(
                "Bearer test-token",
                null,
                new AuthorizationCheckHttpRequest(organization.id(), "org.members.invite"));

        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        AuthorizationDecisionHttpResponse body = assertInstanceOf(
                AuthorizationDecisionHttpResponse.class,
                envelope.data());
        assertEquals(true, body.allowed());
    }

    @Test
    void repositoryApiKeyEndpointsReturnSecretOnlyOnCreate() {
        OrganizationApplicationService service = service();
        var organizationResource = resourceWithUser(service, USER_ID, "owner@example.com");
        var controlPlaneResource = new RepositoryControlPlaneResource(service, fixedUser(USER_ID, "owner@example.com"));
        OrganizationHttpResponse organization = createOrganization(organizationResource);
        RepositoryHttpResponse repository = registerRepository(organizationResource, organization);

        Response createResponse = controlPlaneResource.createRepositoryApiKey(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                new CreateRepositoryApiKeyHttpRequest(
                        "CI uploads",
                        List.of("uploads:create", "uploads:read"),
                        List.of("main"),
                        NOW.plusSeconds(3600)));

        assertEquals(201, createResponse.getStatus());
        RepositoryApiKeyHttpResponse created = responseBody(createResponse, RepositoryApiKeyHttpResponse.class);
        assertTrue(created.apiKey().startsWith("vc_repo_"));

        Response listResponse = controlPlaneResource.listRepositoryApiKeys(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id());
        assertEquals(200, listResponse.getStatus());
        List<?> listed = assertInstanceOf(List.class, responseEnvelope(listResponse).data());
        RepositoryApiKeyHttpResponse listedKey = assertInstanceOf(RepositoryApiKeyHttpResponse.class, listed.getFirst());
        assertEquals(created.keyPrefix(), listedKey.keyPrefix());
        assertEquals(null, listedKey.apiKey());

        Response revokeResponse = controlPlaneResource.revokeRepositoryApiKey(
                "Bearer test-token",
                null,
                organization.id(),
                repository.id(),
                created.id());
        assertEquals(200, revokeResponse.getStatus());
        RepositoryApiKeyHttpResponse revoked = responseBody(revokeResponse, RepositoryApiKeyHttpResponse.class);
        assertEquals(NOW, revoked.revokedAt());
        assertEquals(null, revoked.apiKey());
    }

    @Test
    void mapsMissingAuthToUnauthorized() {
        var resource = new OrganizationResource(
                service(),
                context -> {
                    throw new OrganizationException("unauthorized", "Authentication is required");
                });

        Response response = resource.listOrganizations(null, null);

        assertEquals(401, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("unauthorized", error.error().code());
    }

    @Test
    void jaxRsApplicationRegistersRepositoryControlPlaneResources() {
        var resourceClasses = new OrganizationApplication().getClasses();

        assertEquals(true, resourceClasses.contains(RepositoryControlPlaneResource.class));
        assertEquals(true, resourceClasses.contains(InternalRepositoryConfigResource.class));
    }

    private static OrganizationResource resource() {
        return resourceWithUser("owner@example.com");
    }

    private static OrganizationResource resourceWithUser(String email) {
        return resourceWithUser(service(), USER_ID, email);
    }

    private static OrganizationResource resourceWithUser(OrganizationApplicationService service, UUID userId, String email) {
        return new OrganizationResource(service, fixedUser(userId, email));
    }

    private static OrganizationHttpResponse createOrganization(OrganizationResource resource) {
        Response createResponse = resource.createOrganization(
                "Bearer test-token",
                null,
                new CreateOrganizationHttpRequest("Acme Engineering", UUID.randomUUID() + "-acme", "team"));
        return assertInstanceOf(
                OrganizationHttpResponse.class,
                assertInstanceOf(ApiResponse.class, createResponse.getEntity()).data());
    }

    private static RepositoryHttpResponse registerRepository(
            OrganizationResource resource,
            OrganizationHttpResponse organization) {
        Response response = resource.registerRepository(
                "Bearer test-token",
                null,
                organization.id(),
                new CreateRepositoryHttpRequest(
                        "github",
                        UUID.randomUUID().toString(),
                        "acme/payments-api",
                        "main",
                        "private"));
        return responseBody(response, RepositoryHttpResponse.class);
    }

    private static ApiResponse<?> responseEnvelope(Response response) {
        return assertInstanceOf(ApiResponse.class, response.getEntity());
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        return assertInstanceOf(type, responseEnvelope(response).data());
    }

    private static OrganizationApplicationService service() {
        return new OrganizationApplicationService(
                new InMemoryOrganizationRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserPrincipalResolver fixedUser(UUID userId, String email) {
        return new UserPrincipalResolver() {
            @Override
            public AuthenticatedUser resolve(UserAuthContext context) {
                return new AuthenticatedUser(userId, email);
            }
        };
    }
}
