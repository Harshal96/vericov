package dev.vericov.organization.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryComponentContextServiceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VIEWER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-05-25T15:00:00Z");

    @Test
    void adminCreatesUpdatesListsAndResolvesRepositoryComponents() {
        TestFixture fixture = new TestFixture();
        OrganizationDetails organization = fixture.createOrganization();
        RepositoryDetails repository = fixture.createRepository(organization);

        RepositoryComponentDetails payments = fixture.service.createRepositoryComponent(new CreateRepositoryComponentCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                "payments",
                "Payments service",
                List.of("src/payments/**", "libs/billing/*.java"),
                List.of("@acme/payments"),
                "high",
                Map.of("runtime", "jvm"),
                "active"));
        RepositoryComponentDetails internals = fixture.service.createRepositoryComponent(new CreateRepositoryComponentCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                "payments-internals",
                null,
                List.of("src/payments/internal/**"),
                List.of("@acme/payments-internals"),
                "critical",
                Map.of(),
                "active"));

        RepositoryComponentDetails updated = fixture.service.updateRepositoryComponent(new UpdateRepositoryComponentCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                payments.id(),
                "payments-api",
                null,
                List.of("src/payments/**"),
                List.of("@acme/payments", "@acme/platform"),
                null,
                Map.of("runtime", "jvm", "tier", "1"),
                null));

        assertEquals("payments-api", updated.name());
        assertEquals(List.of("@acme/payments", "@acme/platform"), updated.owners());
        assertEquals(2, fixture.service.listRepositoryComponents(USER_ID, organization.id(), repository.id()).size());

        List<RepositoryPathResolutionDetails> resolutions = fixture.service.resolveRepositoryPaths(new ResolveRepositoryPathsCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                List.of("src/payments/internal/Vault.java", "src/payments/App.java")));

        assertEquals(internals.id(), resolutions.get(0).componentId());
        assertEquals("@acme/payments-internals", resolutions.get(0).primaryOwner());
        assertEquals("critical", resolutions.get(0).criticality());
        assertEquals("component", resolutions.get(0).source());
        assertEquals(payments.id(), resolutions.get(1).componentId());
        assertEquals(List.of("@acme/payments", "@acme/platform"), resolutions.get(1).owners());
    }

    @Test
    void rejectsUnsafeComponentPathPatternsAndViewerWrites() {
        TestFixture fixture = new TestFixture();
        OrganizationDetails organization = fixture.createOrganization();
        RepositoryDetails repository = fixture.createRepository(organization);
        fixture.service.addMembership(new CreateMembershipCommand(USER_ID, organization.id(), VIEWER_ID, "viewer", "active"));

        OrganizationException unsafePattern = assertThrows(
                OrganizationException.class,
                () -> fixture.service.createRepositoryComponent(new CreateRepositoryComponentCommand(
                        USER_ID,
                        organization.id(),
                        repository.id(),
                        "unsafe",
                        null,
                        List.of("../secrets/**"),
                        List.of("@acme/security"),
                        "medium",
                        Map.of(),
                        "active")));
        assertEquals("validation_error", unsafePattern.code());

        OrganizationException forbidden = assertThrows(
                OrganizationException.class,
                () -> fixture.service.createRepositoryComponent(new CreateRepositoryComponentCommand(
                        VIEWER_ID,
                        organization.id(),
                        repository.id(),
                        "viewer-owned",
                        null,
                        List.of("src/viewer/**"),
                        List.of("@acme/viewers"),
                        "medium",
                        Map.of(),
                        "active")));
        assertEquals("forbidden", forbidden.code());
    }

    @Test
    void syncsCodeownersPackageNodesAndBuildsInternalCoverageContext() {
        TestFixture fixture = new TestFixture();
        OrganizationDetails organization = fixture.createOrganization();
        RepositoryDetails repository = fixture.createRepository(organization);
        RepositoryComponentDetails payments = fixture.service.createRepositoryComponent(new CreateRepositoryComponentCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                "payments",
                null,
                List.of("src/payments/**"),
                List.of("@acme/payments"),
                "high",
                Map.of(),
                "active"));

        RepositoryContextSyncDetails synced = fixture.service.syncRepositoryContext(new SyncRepositoryContextCommand(
                USER_ID,
                organization.id(),
                repository.id(),
                "abc123",
                """
                # CODEOWNERS
                *.md @acme/docs
                /src/payments/ @acme/codeowners-payments
                """,
                List.of(new RepositoryPackageNodeInput(
                        payments.id(),
                        "payments-service",
                        "src/payments",
                        "src/payments/package.json",
                        "npm",
                        Map.of("workspace", true)))));

        assertEquals(2, synced.ownerRules().size());
        assertEquals(1, synced.packageNodes().size());

        RepositoryCoverageContextDetails context = fixture.service.getInternalCoverageContext(repository.id(), "abc123");

        assertEquals(repository.id(), context.repositoryId());
        assertEquals("abc123", context.commitSha());
        assertEquals(1, context.components().size());
        assertEquals(2, context.ownerRules().size());
        assertEquals(1, context.packageNodes().size());
        assertEquals("payments-service", context.packageNodes().getFirst().packageName());
    }

    private static final class TestFixture {
        private final MutableClock clock = new MutableClock();
        private final OrganizationApplicationService service = new OrganizationApplicationService(
                new InMemoryOrganizationRepository(),
                clock);

        private OrganizationDetails createOrganization() {
            return service.createOrganization(new CreateOrganizationCommand(USER_ID, "Acme", "acme", "team"));
        }

        private RepositoryDetails createRepository(OrganizationDetails organization) {
            return service.registerRepository(new CreateRepositoryCommand(
                    USER_ID,
                    organization.id(),
                    "github",
                    "123",
                    "acme/payments",
                    "main",
                    "private"));
        }
    }

    private static final class MutableClock extends Clock {
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
            return NOW;
        }
    }
}
