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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void adminCreatesListsAndRevokesRepositoryApiKeyWithoutPersistingRawSecret() {
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

        RepositoryApiKeyDetails created = fixture.service.createRepositoryApiKey(new CreateRepositoryApiKeyCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                "CI uploads",
                List.of("uploads:create", "uploads:read"),
                List.of("main", "release/*"),
                NOW.plusSeconds(3600)));

        assertTrue(created.plaintextKey().startsWith("vc_repo_"));
        assertEquals(repository.id(), created.repositoryId());
        assertEquals("CI uploads", created.name());
        assertEquals(List.of("uploads:create", "uploads:read"), created.scopes());
        assertEquals(List.of("main", "release/*"), created.branchAllowPatterns());
        assertEquals(NOW.plusSeconds(3600), created.expiresAt());
        assertNull(created.revokedAt());

        var listed = fixture.service.listRepositoryApiKeys(USER_ID, organization.id(), repository.id());
        assertEquals(1, listed.size());
        assertNull(listed.getFirst().plaintextKey());
        assertEquals(created.keyPrefix(), listed.getFirst().keyPrefix());

        RepositoryApiKeyDetails revoked = fixture.service.revokeRepositoryApiKey(new RevokeRepositoryApiKeyCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                created.id()));

        assertEquals(NOW, revoked.revokedAt());
        assertNull(revoked.plaintextKey());
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
    void filtersCoverageTrendsByDateWindow() {
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
        fixture.repository.saveCoverageReport(reportSummary(
                repository, "abc121", "main", NOW.minusSeconds(180), 20, 40, 2, 10, 2, 5, 10, 25));
        fixture.repository.saveCoverageReport(reportSummary(
                repository, "abc122", "main", NOW.minusSeconds(120), 30, 40, 6, 10, 4, 5, 18, 25));
        fixture.repository.saveCoverageReport(reportSummary(
                repository, "abc123", "main", NOW.minusSeconds(60), 33, 40, 8, 10, 5, 5, 20, 25));

        CoverageTrendDetails trend = fixture.service.listCoverageTrends(new ListCoverageTrendsQuery(
                USER_ID,
                organization.id(),
                repository.id(),
                "main",
                "line",
                NOW.minusSeconds(125),
                NOW.minusSeconds(55),
                10));

        assertEquals(2, trend.points().size());
        assertEquals("abc122", trend.points().get(0).commitSha());
        assertEquals("abc123", trend.points().get(1).commitSha());
    }

    @Test
    void trendsAndBadgesExposeEveryCoverageMetric() {
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
        fixture.repository.saveCoverageReport(reportSummary(
                repository, "abc123", "main", NOW, 33, 40, 8, 10, 5, 5, 20, 25));
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

        assertMetricTrendAndBadge(fixture, organization.id(), repository.id(), token.token(), "line", "82.5", "82.5%");
        assertMetricTrendAndBadge(fixture, organization.id(), repository.id(), token.token(), "branch", "80", "80%");
        assertMetricTrendAndBadge(fixture, organization.id(), repository.id(), token.token(), "function", "100", "100%");
        assertMetricTrendAndBadge(fixture, organization.id(), repository.id(), token.token(), "statement", "80", "80%");
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

    @Test
    void createsCoverageDebtSuccessfully() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));

        Instant expiry = NOW.plusSeconds(3600);
        var command = new CreateCoverageDebtCommand(
                USER_ID,
                org.id(),
                repo.id(),
                null,
                null,
                null,
                "commit-sha",
                12,
                "line",
                "src/App.java",
                10,
                null,
                "symbolName",
                "high",
                "Reason for debt item",
                "john.doe@example.com",
                expiry,
                null,
                Map.of("meta", "value")
        );

        var debt = fixture.service.createCoverageDebt(command);

        assertEquals(org.id(), debt.organizationId());
        assertEquals(repo.id(), debt.repositoryId());
        assertEquals("line", debt.targetType());
        assertEquals("src/App.java", debt.filePath());
        assertEquals(10, debt.lineStart());
        assertNull(debt.lineEnd());
        assertEquals("high", debt.riskLevel());
        assertEquals("Reason for debt item", debt.reason());
        assertEquals("john.doe@example.com", debt.owner());
        assertEquals("active", debt.status());
        assertEquals(expiry, debt.expiresAt());
        assertEquals(USER_ID, debt.createdByUserId());

        // Check event emitted
        var events = fixture.repository.listCoverageDebtEvents(debt.id());
        assertEquals(1, events.size());
        assertEquals("created", events.getFirst().eventType());
        assertEquals(USER_ID, events.getFirst().actorUserId());
    }

    @Test
    void rejectsCreateCoverageDebtValidationErrors() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));

        Instant expiry = NOW.plusSeconds(3600);

        // Reason too short
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "line", "src/App.java", 10, null, null, "high",
                "short", "owner", expiry, null, Map.of()
        )));

        // Owner empty
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "line", "src/App.java", 10, null, null, "high",
                "valid reason text", "", expiry, null, Map.of()
        )));

        // Expiry in past
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "line", "src/App.java", 10, null, null, "high",
                "valid reason text", "owner", NOW.minusSeconds(10), null, Map.of()
        )));

        // Expiry null
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "line", "src/App.java", 10, null, null, "high",
                "valid reason text", "owner", null, null, Map.of()
        )));

        // Target type line with line_end set
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "line", "src/App.java", 10, 20, null, "high",
                "valid reason text", "owner", expiry, null, Map.of()
        )));

        // Target type range with invalid coordinates
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "range", "src/App.java", 20, 10, null, "high",
                "valid reason text", "owner", expiry, null, Map.of()
        )));

        // File path traversal
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "../outside.java", null, null, null, "high",
                "valid reason text", "owner", expiry, null, Map.of()
        )));

        // File path absolute
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "/abs/path.java", null, null, null, "high",
                "valid reason text", "owner", expiry, null, Map.of()
        )));
    }

    @Test
    void validatesCriticalDebtPolicyDefaults() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));

        Instant expiry = NOW.plusSeconds(3600);

        // Critical path by default is blocked
        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "src/App.java", null, null, null, "critical",
                "valid reason text", "owner", expiry, null, Map.of()
        )));

        // Allow critical debt via Org defaults
        fixture.service.upsertPolicyDefaults(new UpsertPolicyDefaultsCommand(
                USER_ID,
                org.id(),
                Map.of("allow_critical_debt", true),
                1
        ));

        var debt = fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "src/App.java", null, null, null, "critical",
                "valid reason text", "owner", expiry, null, Map.of()
        ));
        assertEquals("critical", debt.riskLevel());

        // Block it again using Repository config override
        fixture.service.upsertRepositoryConfig(new UpsertRepositoryConfigCommand(
                USER_ID,
                org.id(),
                repo.id(),
                Map.of("allow_critical_debt", false),
                1
        ));

        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "src/App.java", null, null, null, "critical",
                "valid reason text2", "owner", expiry, null, Map.of()
        )));
    }

    @Test
    void preventsViewerFromWritingCoverageDebt() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));
        fixture.service.addMembership(new CreateMembershipCommand(
                USER_ID, org.id(), OTHER_USER_ID, "viewer", "active"
        ));

        Instant expiry = NOW.plusSeconds(3600);
        var command = new CreateCoverageDebtCommand(
                OTHER_USER_ID,
                org.id(),
                repo.id(),
                null,
                null,
                null,
                "commit-sha",
                12,
                "file",
                "src/App.java",
                null,
                null,
                null,
                "high",
                "Reason for debt item",
                "john.doe@example.com",
                expiry,
                null,
                Map.of()
        );

        assertThrows(OrganizationException.class, () -> fixture.service.createCoverageDebt(command));
    }

    @Test
    void updatesResolvesAndRevokesCoverageDebt() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));

        Instant expiry = NOW.plusSeconds(3600);
        var debt = fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "src/App.java", null, null, null, "high",
                "Reason for debt item", "owner", expiry, null, Map.of()
        ));

        // Update
        Instant nextExpiry = NOW.plusSeconds(7200);
        var updated = fixture.service.updateCoverageDebt(new UpdateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), debt.id(), "updated owner", "medium", "Updated reason text", nextExpiry, null, Map.of()
        ));

        assertEquals("updated owner", updated.owner());
        assertEquals("Updated reason text", updated.reason());
        assertEquals(nextExpiry, updated.expiresAt());
        assertEquals("medium", updated.riskLevel());

        // Resolve
        var resolved = fixture.service.resolveCoverageDebt(new ResolveCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), debt.id()
        ));
        assertEquals("resolved", resolved.status());
        assertEquals(NOW, resolved.resolvedAt());
        assertEquals(USER_ID, resolved.resolvedByUserId());

        // Reject update or revoke after resolved
        assertThrows(OrganizationException.class, () -> fixture.service.updateCoverageDebt(new UpdateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), debt.id(), "owner", "high", "reason long enough", nextExpiry, null, Map.of()
        )));
        assertThrows(OrganizationException.class, () -> fixture.service.revokeCoverageDebt(new RevokeCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), debt.id()
        )));
    }

    @Test
    void revokesCoverageDebtSuccessfully() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));

        Instant expiry = NOW.plusSeconds(3600);
        var debt = fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "src/App.java", null, null, null, "high",
                "Reason for debt item", "owner", expiry, null, Map.of()
        ));

        var revoked = fixture.service.revokeCoverageDebt(new RevokeCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), debt.id()
        ));
        assertEquals("revoked", revoked.status());
        assertEquals(NOW, revoked.revokedAt());
        assertEquals(USER_ID, revoked.revokedByUserId());
    }

    @Test
    void readTimeNormalizationOfExpiredDebt() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));

        Instant expiry = NOW.plusSeconds(3600);
        var debt = fixture.service.createCoverageDebt(new CreateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), null, null, null, "sha", 1, "file", "src/App.java", null, null, null, "high",
                "Reason for debt item", "owner", expiry, null, Map.of()
        ));

        // Before expiry
        var retrieved1 = fixture.service.getCoverageDebt(new GetCoverageDebtQuery(USER_ID, org.id(), repo.id(), debt.id()));
        assertEquals("active", retrieved1.status());

        // Advance clock past expiry
        fixture.clock.advanceSeconds(3601);

        var retrieved2 = fixture.service.getCoverageDebt(new GetCoverageDebtQuery(USER_ID, org.id(), repo.id(), debt.id()));
        assertEquals("expired", retrieved2.status());

        // Listing should also normalise
        var listed = fixture.service.listCoverageDebts(new ListCoverageDebtsQuery(
                USER_ID, org.id(), repo.id(), null, null, null, null, null, true, null, 100
        ));
        assertEquals("expired", listed.getFirst().status());

        // Update expired back to active by extending expiry
        Instant futureExpiry = fixture.clock.instant().plusSeconds(3600);
        var updated = fixture.service.updateCoverageDebt(new UpdateCoverageDebtCommand(
                USER_ID, org.id(), repo.id(), debt.id(), null, null, null, futureExpiry, null, null
        ));
        assertEquals("active", updated.status());
    }

    @Test
    void listsCoverageGapsRankedWithFilters() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));
        var low = fixture.repository.saveCoverageGap(gap(org, repo, "src/Low.java", 5, "low", new BigDecimal("20.0"), "active", List.of("@acme/app"), "create_debt"));
        var highDebt = fixture.repository.saveCoverageGap(gap(org, repo, "src/Debt.java", 6, "high", new BigDecimal("70.0"), "debt_suppressed", List.of("@acme/app"), "create_debt"));
        var critical = fixture.repository.saveCoverageGap(gap(org, repo, "src/Critical.java", 7, "critical", new BigDecimal("91.0"), "active", List.of("@acme/core"), "add_test"));
        var high = fixture.repository.saveCoverageGap(gap(org, repo, "src/High.java", 8, "high", new BigDecimal("72.0"), "active", List.of("@acme/app"), "add_test"));
        var mismatch = fixture.repository.saveCoverageGap(gap(
                org,
                repo,
                "src/Mismatch.java",
                9,
                "high",
                new BigDecimal("71.0"),
                "active",
                List.of("@acme/app"),
                "inspect_instrumentation",
                "possible_path_mismatch"));

        var gaps = fixture.service.listCoverageGaps(new ListCoverageGapsQuery(
                USER_ID,
                org.id(),
                repo.id(),
                "sha",
                1,
                null,
                null,
                "high",
                null,
                null,
                null,
                false,
                100));

        assertEquals(List.of(critical.id(), high.id(), mismatch.id()), gaps.stream().map(CoverageGapFindingDetails::id).toList());
        assertFalse(gaps.stream().map(CoverageGapFindingDetails::id).toList().contains(low.id()));
        assertFalse(gaps.stream().map(CoverageGapFindingDetails::id).toList().contains(highDebt.id()));

        var owned = fixture.service.listCoverageGaps(new ListCoverageGapsQuery(
                USER_ID,
                org.id(),
                repo.id(),
                null,
                null,
                null,
                "@acme/app",
                null,
                "high",
                "active",
                null,
                true,
                100));

        assertEquals(List.of(high.id(), mismatch.id()), owned.stream().map(CoverageGapFindingDetails::id).toList());

        var mismatches = fixture.service.listCoverageGaps(new ListCoverageGapsQuery(
                USER_ID,
                org.id(),
                repo.id(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "possible_path_mismatch",
                true,
                100));

        assertEquals(List.of(mismatch.id()), mismatches.stream().map(CoverageGapFindingDetails::id).toList());
    }

    @Test
    void fixFirstCoverageGapsExcludesDebtSourceRequiredAndOverlappingTargets() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));
        var first = fixture.repository.saveCoverageGap(gap(org, repo, "src/App.java", 10, "high", new BigDecimal("80.0"), "active", List.of("@acme/app"), "add_test"));
        fixture.repository.saveCoverageGap(gap(org, repo, "src/App.java", 10, "high", new BigDecimal("75.0"), "active", List.of("@acme/app"), "add_test"));
        fixture.repository.saveCoverageGap(gap(org, repo, "src/Source.java", 20, "critical", new BigDecimal("95.0"), "active", List.of("@acme/app"), "run_source_explain"));
        var second = fixture.repository.saveCoverageGap(gap(org, repo, "src/Other.java", 30, "high", new BigDecimal("78.0"), "active", List.of("@acme/app"), "add_test"));

        var gaps = fixture.service.listFixFirstCoverageGaps(new ListFixFirstCoverageGapsQuery(
                USER_ID,
                org.id(),
                repo.id(),
                "sha",
                1,
                false,
                5));

        assertEquals(List.of(first.id(), second.id()), gaps.stream().map(CoverageGapFindingDetails::id).toList());
    }

    @Test
    void validatesCoverageRiskPolicyOverrides() {
        TestFixture fixture = new TestFixture();
        var org = fixture.service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        var repo = fixture.service.registerRepository(new CreateRepositoryCommand(USER_ID, org.id(), "github", "1", "a/b", "main", "private"));
        fixture.service.createRepositoryComponent(new CreateRepositoryComponentCommand(
                USER_ID,
                org.id(),
                repo.id(),
                "auth",
                null,
                List.of("src/auth/**"),
                List.of("@acme/auth"),
                "high",
                Map.of(),
                "active"));

        var valid = fixture.service.createRepositoryPolicy(new CreateRepositoryPolicyCommand(
                USER_ID,
                org.id(),
                repo.id(),
                "Risk policy",
                null,
                "coverage",
                "repository",
                null,
                Map.of("risk", Map.of(
                        "path_overrides", List.of(Map.of("pattern", "src/auth/**", "score_boost", 15)),
                        "component_overrides", List.of(Map.of("component", "auth", "criticality", "critical")),
                        "rank_comments", Map.of("max_items", 5, "min_level", "high"))),
                "active",
                100));
        assertEquals("Risk policy", valid.name());

        OrganizationException badBoost = assertThrows(OrganizationException.class, () ->
                fixture.service.createRepositoryPolicy(new CreateRepositoryPolicyCommand(
                        USER_ID, org.id(), repo.id(), "Bad boost", null, "coverage", "repository", null,
                        Map.of("risk", Map.of("path_overrides", List.of(Map.of("pattern", "src/**", "score_boost", 51)))),
                        "active", 100)));
        assertEquals("validation_error", badBoost.code());

        OrganizationException badPath = assertThrows(OrganizationException.class, () ->
                fixture.service.createRepositoryPolicy(new CreateRepositoryPolicyCommand(
                        USER_ID, org.id(), repo.id(), "Bad path", null, "coverage", "repository", null,
                        Map.of("risk", Map.of("path_overrides", List.of(Map.of("pattern", "/src/**", "score_boost", 1)))),
                        "active", 100)));
        assertEquals("validation_error", badPath.code());

        OrganizationException badComponent = assertThrows(OrganizationException.class, () ->
                fixture.service.createRepositoryPolicy(new CreateRepositoryPolicyCommand(
                        USER_ID, org.id(), repo.id(), "Bad component", null, "coverage", "repository", null,
                        Map.of("risk", Map.of("component_overrides", List.of(Map.of("component", "payments", "criticality", "critical")))),
                        "active", 100)));
        assertEquals("validation_error", badComponent.code());
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

    private static CoverageReportSummary reportSummary(
            RepositoryDetails repository,
            String commitSha,
            String branch,
            Instant createdAt,
            int lineCovered,
            int lineTotal,
            int branchCovered,
            int branchTotal,
            int functionCovered,
            int functionTotal,
            int statementCovered,
            int statementTotal) {
        return new CoverageReportSummary(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.id(),
                UUID.randomUUID(),
                commitSha,
                branch,
                null,
                lineCovered,
                lineTotal,
                branchCovered,
                branchTotal,
                functionCovered,
                functionTotal,
                statementCovered,
                statementTotal,
                createdAt,
                createdAt);
    }

    private static void assertMetricTrendAndBadge(
            TestFixture fixture,
            UUID organizationId,
            UUID repositoryId,
            String token,
            String metric,
            String expectedPercent,
            String expectedMessage) {
        CoverageTrendDetails trend = fixture.service.listCoverageTrends(new ListCoverageTrendsQuery(
                USER_ID,
                organizationId,
                repositoryId,
                "main",
                metric,
                null,
                null,
                10));
        CoverageBadgeDetails badge = fixture.service.getCoverageBadge(new GetCoverageBadgeCommand(
                null,
                organizationId,
                repositoryId,
                token,
                null,
                metric));

        assertEquals(1, trend.points().size());
        assertEquals(metric, trend.points().getFirst().metric());
        assertEquals(0, new BigDecimal(expectedPercent).compareTo(trend.points().getFirst().percent()));
        assertEquals(expectedMessage, badge.message());
        assertEquals(metric, badge.metric());
    }

    private static CoverageGapFindingDetails gap(
            OrganizationDetails organization,
            RepositoryDetails repository,
            String filePath,
            int line,
            String riskLevel,
            BigDecimal riskScore,
            String status,
            List<String> owners,
            String nextAction) {
        return gap(
                organization,
                repository,
                filePath,
                line,
                riskLevel,
                riskScore,
                status,
                owners,
                nextAction,
                "new_uncovered_changed_line");
    }

    private static CoverageGapFindingDetails gap(
            OrganizationDetails organization,
            RepositoryDetails repository,
            String filePath,
            int line,
            String riskLevel,
            BigDecimal riskScore,
            String status,
            List<String> owners,
            String nextAction,
            String reasonCode) {
        return new CoverageGapFindingDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                organization.id(),
                repository.id(),
                UUID.randomUUID(),
                null,
                null,
                "sha",
                1,
                filePath,
                "line",
                line,
                line,
                null,
                reasonCode,
                "Added executable line " + line + " is uncovered in the head report.",
                "high",
                riskScore,
                riskLevel,
                owners,
                nextAction,
                status,
                Map.of("score", Map.of("total", riskScore, "level", riskLevel)),
                NOW,
                NOW);
    }
}
