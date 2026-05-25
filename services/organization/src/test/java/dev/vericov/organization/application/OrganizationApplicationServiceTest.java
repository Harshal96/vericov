package dev.vericov.organization.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrganizationApplicationServiceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID THIRD_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String OTHER_EMAIL = "teammate@example.com";
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void createsOrganizationTenantAndOwnerMembership() {
        TestFixture fixture = new TestFixture();

        OrganizationDetails organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));

        assertEquals("Acme Engineering", organization.name());
        assertEquals("acme", organization.slug());
        assertEquals("team", organization.plan());
        assertEquals("active", organization.status());
        assertEquals(NOW, organization.createdAt());

        var memberships = fixture.service.listMemberships(USER_ID, organization.id());
        assertEquals(1, memberships.size());
        assertEquals(USER_ID, memberships.getFirst().supabaseUserId());
        assertEquals("owner", memberships.getFirst().role());
        assertEquals("active", memberships.getFirst().status());
    }

    @Test
    void listsOnlyOrganizationsVisibleToUser() {
        TestFixture fixture = new TestFixture();
        var visible = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        fixture.service.createOrganization(new CreateOrganizationCommand(
                OTHER_USER_ID,
                "Other Org",
                "other-org",
                "free"));

        var organizations = fixture.service.listOrganizations(USER_ID);

        assertEquals(1, organizations.size());
        assertEquals(visible.id(), organizations.getFirst().id());
    }

    @Test
    void rejectsInvalidSlug() {
        TestFixture fixture = new TestFixture();

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.createOrganization(new CreateOrganizationCommand(
                        USER_ID,
                        "Acme Engineering",
                        "Acme!",
                        "team")));

        assertEquals("validation_error", exception.code());
        assertFalse(fixture.repository.slugExists("Acme!"));
    }

    @Test
    void preventsViewerFromAddingMembers() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID,
                organization.id(),
                OTHER_USER_ID,
                "viewer",
                "active"));

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.addMembership(new CreateMembershipCommand(
                        OTHER_USER_ID,
                        organization.id(),
                        THIRD_USER_ID,
                        "developer",
                        "active")));

        assertEquals("forbidden", exception.code());
    }

    @Test
    void adminRegistersRepositoryForOrganization() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));

        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                " GitHub ",
                " 123456789 ",
                " Acme/Payments-API ",
                " main ",
                " private "));

        assertEquals(organization.id(), repository.organizationId());
        assertEquals(organization.tenantId(), repository.tenantId());
        assertEquals("github", repository.provider());
        assertEquals("123456789", repository.providerRepositoryId());
        assertEquals("Acme/Payments-API", repository.fullName());
        assertEquals("main", repository.defaultBranch());
        assertEquals("private", repository.visibility());
        assertEquals("active", repository.status());
        assertEquals(NOW, repository.createdAt());
        assertEquals(NOW, repository.updatedAt());

        var repositories = fixture.service.listRepositories(USER_ID, organization.id());
        assertEquals(1, repositories.size());
        assertEquals(repository.id(), repositories.getFirst().id());
    }

    @Test
    void rejectsDuplicateRepositoryProviderIdentityInOrganization() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.registerRepository(new CreateRepositoryCommand(
                        USER_ID,
                        organization.id(),
                        "github",
                        "123456789",
                        "acme/payments-api-copy",
                        "main",
                        "private")));

        assertEquals("conflict", exception.code());
    }

    @Test
    void preventsViewerFromRegisteringRepository() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID,
                organization.id(),
                OTHER_USER_ID,
                "viewer",
                "active"));

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.registerRepository(new CreateRepositoryCommand(
                        OTHER_USER_ID,
                        organization.id(),
                        "github",
                        "123456789",
                        "acme/payments-api",
                        "main",
                        "private")));

        assertEquals("forbidden", exception.code());
    }

    @Test
    void adminUpdatesRepositorySettings() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));

        RepositoryDetails updated = fixture.service.updateRepository(new UpdateRepositoryCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                "acme/payments-service",
                "release/2026",
                "internal",
                "active"));

        assertEquals(repository.id(), updated.id());
        assertEquals("acme/payments-service", updated.fullName());
        assertEquals("release/2026", updated.defaultBranch());
        assertEquals("internal", updated.visibility());
        assertEquals("active", updated.status());
        assertEquals(NOW, updated.updatedAt());
    }

    @Test
    void adminManagesRepositoryPolicyDefaultsConfigPoliciesAndGates() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));

        PolicyDefaultsDetails defaults = fixture.service.upsertPolicyDefaults(new UpsertPolicyDefaultsCommand(
                USER_ID,
                organization.id(),
                Map.of("coverage", Map.of("project", new BigDecimal("80"))),
                1));
        RepositoryConfigDetails config = fixture.service.upsertRepositoryConfig(new UpsertRepositoryConfigCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                Map.of("coverage", Map.of("patch", new BigDecimal("75"))),
                2));
        RepositoryPolicyDetails policy = fixture.service.createRepositoryPolicy(new CreateRepositoryPolicyCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                "Coverage floor",
                "Minimum project coverage",
                "coverage",
                "repository",
                null,
                Map.of("minimum", new BigDecimal("80")),
                "active",
                10));
        RepositoryPolicyDetails updatedPolicy = fixture.service.updateRepositoryPolicy(new UpdateRepositoryPolicyCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                policy.id(),
                "Strict coverage floor",
                null,
                null,
                null,
                null,
                Map.of("minimum", new BigDecimal("85")),
                null,
                5));
        List<RepositoryGateDetails> gates = fixture.service.replaceRepositoryGates(new UpsertRepositoryGatesCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                List.of(new RepositoryGateDetails(
                        UUID.randomUUID(),
                        repository.tenantId(),
                        organization.id(),
                        repository.id(),
                        "Project coverage",
                        "project_coverage",
                        "line",
                        new BigDecimal("80"),
                        null,
                        true,
                        Map.of("severity", "required"),
                        "active",
                        NOW,
                        NOW))));

        EffectiveRepositoryConfig effective = fixture.service.getEffectiveRepositoryConfig(
                USER_ID,
                organization.id(),
                repository.id());

        assertEquals(Map.of("coverage", Map.of("project", new BigDecimal("80"))), defaults.defaults());
        assertEquals("valid", config.validationStatus());
        assertEquals(2, config.schemaVersion());
        assertEquals("Strict coverage floor", updatedPolicy.name());
        assertEquals(5, updatedPolicy.priority());
        assertEquals(1, gates.size());
        assertEquals("Project coverage", gates.getFirst().name());
        assertEquals(defaults.defaults(), effective.orgDefaults());
        assertEquals(config.config(), effective.repositoryConfig());
        assertEquals(1, effective.policies().size());
        assertEquals(1, effective.gates().size());
    }

    @Test
    void viewerCanReadButCannotMutateRepositoryControls() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));
        fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID,
                organization.id(),
                OTHER_USER_ID,
                "viewer",
                "active"));

        EffectiveRepositoryConfig readable = fixture.service.getEffectiveRepositoryConfig(
                OTHER_USER_ID,
                organization.id(),
                repository.id());
        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.upsertRepositoryConfig(new UpsertRepositoryConfigCommand(
                        OTHER_USER_ID,
                        organization.id(),
                        repository.id(),
                        Map.of("coverage", Map.of("patch", new BigDecimal("70"))),
                        1)));

        assertEquals(repository.id(), readable.repositoryId());
        assertEquals("forbidden", exception.code());
    }

    @Test
    void adminConfiguresTokenProtectedCoverageBadge() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));
        fixture.repository.saveCoverageReport(new CoverageReportSummary(
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
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));

        RepositoryBadgeSettingsDetails settings = fixture.service.upsertRepositoryBadgeSettings(
                new UpsertRepositoryBadgeSettingsCommand(
                        USER_ID,
                        organization.id(),
                        repository.id(),
                        true,
                        "main",
                        "line",
                        "Coverage <Main>",
                        Map.of(
                                "brightgreen", new BigDecimal("90"),
                                "green", new BigDecimal("80"),
                                "yellow", new BigDecimal("60"))));
        BadgeTokenDetails token = fixture.service.rotateRepositoryBadgeToken(new RotateRepositoryBadgeTokenCommand(
                USER_ID,
                organization.id(),
                repository.id()));
        CoverageBadgeDetails badge = fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null));

        assertEquals(repository.id(), settings.repositoryId());
        assertEquals("vc_badge_", token.token().substring(0, 9));
        assertEquals("Coverage <Main>", badge.label());
        assertEquals("82.5%", badge.message());
        assertEquals("green", badge.color());
        assertEquals("abc123", badge.commitSha());
    }

    @Test
    void coverageBadgeUsesCachedValueUntilTokenCacheExpires() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));
        fixture.service.upsertRepositoryBadgeSettings(new UpsertRepositoryBadgeSettingsCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                true,
                "main",
                "line",
                "coverage",
                Map.of()));
        BadgeTokenDetails token = fixture.service.rotateRepositoryBadgeToken(new RotateRepositoryBadgeTokenCommand(
                USER_ID,
                organization.id(),
                repository.id()));
        fixture.repository.saveCoverageReport(new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "abc123",
                "main",
                33,
                40,
                0,
                0,
                0,
                0,
                0,
                0,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));

        CoverageBadgeDetails first = fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null));
        fixture.repository.saveCoverageReport(new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "def456",
                "main",
                40,
                40,
                0,
                0,
                0,
                0,
                0,
                0,
                NOW.minusSeconds(30),
                NOW.minusSeconds(30)));
        CoverageBadgeDetails cached = fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null));

        fixture.clock.advanceSeconds(61);
        CoverageBadgeDetails refreshed = fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null));

        assertEquals("abc123", first.commitSha());
        assertEquals("abc123", cached.commitSha());
        assertEquals("82.5%", cached.message());
        assertEquals("def456", refreshed.commitSha());
        assertEquals("100%", refreshed.message());
        assertEquals("brightgreen", refreshed.color());
    }

    @Test
    void badgeSettingsUpdateClearsStaleCacheEvenWhenUpdatedAtMatches() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));
        fixture.repository.saveCoverageReport(new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "abc123",
                "main",
                33,
                40,
                0,
                0,
                0,
                0,
                0,
                0,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));
        fixture.service.upsertRepositoryBadgeSettings(new UpsertRepositoryBadgeSettingsCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                true,
                "main",
                "line",
                "coverage",
                Map.of()));
        BadgeTokenDetails token = fixture.service.rotateRepositoryBadgeToken(new RotateRepositoryBadgeTokenCommand(
                USER_ID,
                organization.id(),
                repository.id()));
        fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null));

        fixture.service.upsertRepositoryBadgeSettings(new UpsertRepositoryBadgeSettingsCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                true,
                "main",
                "line",
                "Build Coverage",
                Map.of()));
        CoverageBadgeDetails badge = fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null));

        assertEquals("Build Coverage", badge.label());
    }

    @Test
    void disabledCoverageBadgeRejectsCachedTokenAccess() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));
        fixture.repository.saveCoverageReport(new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "abc123",
                "main",
                33,
                40,
                0,
                0,
                0,
                0,
                0,
                0,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));
        fixture.service.upsertRepositoryBadgeSettings(new UpsertRepositoryBadgeSettingsCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                true,
                "main",
                "line",
                "coverage",
                Map.of()));
        BadgeTokenDetails token = fixture.service.rotateRepositoryBadgeToken(new RotateRepositoryBadgeTokenCommand(
                USER_ID,
                organization.id(),
                repository.id()));
        fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organization.id(),
                repository.id(),
                token.token(),
                null,
                null));

        fixture.service.upsertRepositoryBadgeSettings(new UpsertRepositoryBadgeSettingsCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                false,
                "main",
                "line",
                "coverage",
                Map.of()));
        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                        null,
                        organization.id(),
                        repository.id(),
                        token.token(),
                        null,
                        null)));

        assertEquals("not_found", exception.code());
    }

    @Test
    void disabledCoverageBadgeRejectsUnauthenticatedTokenAccess() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));
        fixture.service.upsertRepositoryBadgeSettings(new UpsertRepositoryBadgeSettingsCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                false,
                "main",
                "line",
                "coverage",
                Map.of()));
        BadgeTokenDetails token = fixture.service.rotateRepositoryBadgeToken(new RotateRepositoryBadgeTokenCommand(
                USER_ID,
                organization.id(),
                repository.id()));

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                        null,
                        organization.id(),
                        repository.id(),
                        token.token(),
                        null,
                        null)));

        assertEquals("not_found", exception.code());
    }

    @Test
    void readsCommitReportsTrendsDashboardsAndGateEvaluations() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        RepositoryDetails repository = fixture.service.registerRepository(new CreateRepositoryCommand(
                USER_ID,
                organization.id(),
                "github",
                "123456789",
                "acme/payments-api",
                "main",
                "private"));
        UUID olderReportId = UUID.randomUUID();
        UUID latestReportId = UUID.randomUUID();
        fixture.repository.saveCoverageReport(new CoverageReportSummary(
                olderReportId,
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                "abc122",
                "main",
                null,
                30,
                40,
                6,
                10,
                4,
                5,
                18,
                25,
                NOW.minusSeconds(120),
                NOW.minusSeconds(120)));
        fixture.repository.saveCoverageReport(new CoverageReportSummary(
                latestReportId,
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
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));
        fixture.repository.saveCoverageFileSummary(new CoverageFileSummaryDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                latestReportId,
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
                NOW.minusSeconds(60)));
        fixture.repository.savePullRequestDiffCoverage(new PullRequestDiffCoverageDetails(
                UUID.randomUUID(),
                latestReportId,
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
                NOW.minusSeconds(30),
                NOW.minusSeconds(30)));
        fixture.repository.saveCoverageLineHits(new CoverageLineHitMapDetails(
                repository.id(),
                latestReportId,
                "abc123",
                Map.of("src/App.java", Map.of(12, 4L, 14, 0L, 20, 0L))));
        fixture.repository.saveGateEvaluation(new GateEvaluationDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                organization.id(),
                repository.id(),
                latestReportId,
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
                NOW.minusSeconds(30)));

        CoverageReportDetails commitReport = fixture.service.getCommitCoverageReport(new GetCommitCoverageReportQuery(
                USER_ID,
                organization.id(),
                repository.id(),
                "abc123",
                true,
                100));
        PullRequestCoverageReportDetails pullRequestReport = fixture.service.getPullRequestCoverageReport(
                new GetPullRequestCoverageReportQuery(
                        USER_ID,
                        organization.id(),
                        repository.id(),
                        42,
                        true,
                        100,
                        true));
        CoverageLineHitMapDetails lineHits = fixture.service.getCoverageLineHits(new GetCoverageLineHitsQuery(
                USER_ID,
                organization.id(),
                repository.id(),
                "abc123",
                "src/App.java"));
        CoverageTrendDetails trend = fixture.service.listCoverageTrends(new ListCoverageTrendsQuery(
                USER_ID,
                organization.id(),
                repository.id(),
                "main",
                "line",
                null,
                null,
                10));
        List<GateEvaluationDetails> gates = fixture.service.listGateEvaluations(new ListGateEvaluationsQuery(
                USER_ID,
                organization.id(),
                repository.id(),
                "main",
                null,
                10));
        RepositoryDashboardDetails repositoryDashboard = fixture.service.getRepositoryDashboard(
                new GetRepositoryDashboardQuery(USER_ID, organization.id(), repository.id(), "main"));
        OrganizationDashboardDetails organizationDashboard = fixture.service.getOrganizationDashboard(
                new GetOrganizationDashboardQuery(USER_ID, organization.id(), "main"));
        List<RepositoryDashboardSummaryDetails> repositoryDashboards = fixture.service.listRepositoryDashboards(
                new ListRepositoryDashboardsQuery(USER_ID, organization.id(), "main"));

        assertEquals("abc123", commitReport.commitSha());
        assertEquals(new BigDecimal("82.5"), commitReport.line().percent());
        assertEquals(1, commitReport.files().size());
        assertEquals("src/App.java", commitReport.files().getFirst().filePath());
        assertEquals(42, pullRequestReport.pullRequestNumber());
        assertEquals("abc123", pullRequestReport.headSha());
        assertEquals("complete", pullRequestReport.diffCoverage().status());
        assertEquals(0, new BigDecimal("50").compareTo(pullRequestReport.diffCoverage().patchLine().percent()));
        assertEquals(1, pullRequestReport.diffCoverage().newlyMissedLineCount());
        assertEquals(1, pullRequestReport.diffCoverage().lostCoverageLineCount());
        assertEquals(2, pullRequestReport.diffCoverage().files().getFirst().lines().size());
        assertEquals(Map.of(12, 4L, 14, 0L, 20, 0L), lineHits.files().get("src/App.java"));
        assertEquals(2, trend.points().size());
        assertEquals("abc122", trend.points().getFirst().commitSha());
        assertEquals("failed", gates.getFirst().status());
        assertEquals("abc123", repositoryDashboard.latestCommitSha());
        assertEquals(new BigDecimal("82.5"), repositoryDashboard.latestLineCoverage().percent());
        assertEquals(1, organizationDashboard.repositoryCount());
        assertEquals(new BigDecimal("82.5"), organizationDashboard.averageLineCoverage());
        assertEquals(1, repositoryDashboards.size());
        assertEquals(repository.id(), repositoryDashboards.getFirst().repositoryId());
    }

    @Test
    void ownerCanUpdateMembershipRoleAndStatus() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        var membership = fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID,
                organization.id(),
                OTHER_USER_ID,
                "viewer",
                "active"));

        var updated = fixture.service.updateMembership(new UpdateMembershipCommand(
                USER_ID,
                organization.id(),
                membership.id(),
                "developer",
                "disabled"));

        assertEquals("developer", updated.role());
        assertEquals("disabled", updated.status());
    }

    @Test
    void adminCanInvitePendingMemberByEmail() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID,
                organization.id(),
                OTHER_USER_ID,
                "admin",
                "active"));

        var invitation = fixture.service.inviteMember(new CreateInvitationCommand(
                OTHER_USER_ID,
                organization.id(),
                " Teammate@Example.com ",
                "developer"));

        assertEquals("teammate@example.com", invitation.email());
        assertEquals("developer", invitation.role());
        assertEquals("pending", invitation.status());
        assertFalse(invitation.acceptanceToken().isBlank());
    }

    @Test
    void invitedUserAcceptsInvitationWithMatchingEmailAndToken() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        var invitation = fixture.service.inviteMember(new CreateInvitationCommand(
                USER_ID,
                organization.id(),
                OTHER_EMAIL,
                "viewer"));

        var membership = fixture.service.acceptInvitation(new AcceptInvitationCommand(
                OTHER_USER_ID,
                OTHER_EMAIL,
                organization.id(),
                invitation.id(),
                invitation.acceptanceToken()));

        assertEquals(OTHER_USER_ID, membership.supabaseUserId());
        assertEquals("viewer", membership.role());
        assertEquals("active", membership.status());
        assertEquals("accepted", fixture.repository.findInvitationById(organization.id(), invitation.id()).orElseThrow().status());
    }

    @Test
    void preventsAdminFromAssigningOwnerRole() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID,
                organization.id(),
                OTHER_USER_ID,
                "admin",
                "active"));

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.inviteMember(new CreateInvitationCommand(
                        OTHER_USER_ID,
                        organization.id(),
                        OTHER_EMAIL,
                        "owner")));

        assertEquals("forbidden", exception.code());
    }

    @Test
    void preventsDisablingLastActiveOwner() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        var ownerMembership = fixture.service.listMemberships(USER_ID, organization.id()).getFirst();

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> fixture.service.updateMembership(new UpdateMembershipCommand(
                        USER_ID,
                        organization.id(),
                        ownerMembership.id(),
                        "admin",
                        "active")));

        assertEquals("last_owner", exception.code());
    }

    @Test
    void authorizationCheckExplainsDeniedActions() {
        TestFixture fixture = new TestFixture();
        var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
                USER_ID,
                "Acme Engineering",
                "acme",
                "team"));
        fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID,
                organization.id(),
                OTHER_USER_ID,
                "viewer",
                "active"));

        var decision = fixture.service.checkAuthorization(new AuthorizationCheckCommand(
                OTHER_USER_ID,
                organization.id(),
                "org.members.invite"));

        assertFalse(decision.allowed());
        assertEquals("forbidden", decision.code());
    }

    private static final class TestFixture {
        private final MutableClock clock = new MutableClock();
        private final InMemoryOrganizationRepository repository = new InMemoryOrganizationRepository();
        private final OrganizationApplicationService service = new OrganizationApplicationService(
                repository,
                clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
