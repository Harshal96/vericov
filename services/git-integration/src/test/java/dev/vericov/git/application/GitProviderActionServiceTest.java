package dev.vericov.git.application;

import dev.vericov.git.application.port.CredentialLease;
import dev.vericov.git.application.port.GitProviderActionPort;
import dev.vericov.git.application.port.IntegrationConfigClient;
import dev.vericov.git.application.port.ResolvedGitIntegration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitProviderActionServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID LEASE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant LEASE_EXPIRES_AT = Instant.parse("2026-05-23T10:05:00Z");

    @Test
    void checkRunFailsFastWhenNoActiveGitBindingExists() {
        RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
        integrationConfigClient.resolveFailure = new GitIntegrationException(
                "not_found",
                "Integration resolution not found");
        GitProviderActionService service = new GitProviderActionService(integrationConfigClient, providerActionPort);

        GitIntegrationException exception = assertThrows(
                GitIntegrationException.class,
                () -> service.createOrUpdateCheckRun(checkRunCommand()));

        assertEquals("not_found", exception.code());
        assertEquals(List.of(new ResolveCall(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", "git.checks")),
                integrationConfigClient.resolveCalls);
        assertTrue(integrationConfigClient.leaseCalls.isEmpty());
        assertTrue(providerActionPort.actions.isEmpty());
    }

    @Test
    void commentUpdateFailsFastWhenCommentsCapabilityIsNotGranted() {
        RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
        integrationConfigClient.resolvedIntegration = resolvedIntegration(Set.of("git.checks"));
        GitProviderActionService service = new GitProviderActionService(integrationConfigClient, providerActionPort);

        GitIntegrationException exception = assertThrows(
                GitIntegrationException.class,
                () -> service.createOrUpdatePrComment(commentCommand()));

        assertEquals("forbidden", exception.code());
        assertEquals("Integration binding does not grant git.comments", exception.getMessage());
        assertEquals(List.of(new ResolveCall(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", "git.comments")),
                integrationConfigClient.resolveCalls);
        assertTrue(integrationConfigClient.leaseCalls.isEmpty());
        assertTrue(providerActionPort.actions.isEmpty());
    }

    @Test
    void checkRunFailsFastWhenResolvedConnectionIsNotActive() {
        RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
        integrationConfigClient.resolvedIntegration = resolvedIntegration(
                Set.of("git.checks"),
                "disabled",
                "active");
        GitProviderActionService service = new GitProviderActionService(integrationConfigClient, providerActionPort);

        GitIntegrationException exception = assertThrows(
                GitIntegrationException.class,
                () -> service.createOrUpdateCheckRun(checkRunCommand()));

        assertEquals("not_found", exception.code());
        assertEquals("Active integration connection not found", exception.getMessage());
        assertEquals(List.of(new ResolveCall(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", "git.checks")),
                integrationConfigClient.resolveCalls);
        assertTrue(integrationConfigClient.leaseCalls.isEmpty());
        assertTrue(providerActionPort.actions.isEmpty());
    }

    @Test
    void checkRunFailsFastWhenResolvedBindingIsNotActive() {
        RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
        integrationConfigClient.resolvedIntegration = resolvedIntegration(
                Set.of("git.checks"),
                "active",
                "disabled");
        GitProviderActionService service = new GitProviderActionService(integrationConfigClient, providerActionPort);

        GitIntegrationException exception = assertThrows(
                GitIntegrationException.class,
                () -> service.createOrUpdateCheckRun(checkRunCommand()));

        assertEquals("not_found", exception.code());
        assertEquals("Active integration binding not found", exception.getMessage());
        assertEquals(List.of(new ResolveCall(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", "git.checks")),
                integrationConfigClient.resolveCalls);
        assertTrue(integrationConfigClient.leaseCalls.isEmpty());
        assertTrue(providerActionPort.actions.isEmpty());
    }

    @Test
    void providerActionReceivesResolvedCredentialKindLeaseDataButNotStoredSecretMaterial() {
        RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
        char[] leasedCredential = "lease-value".toCharArray();
        integrationConfigClient.resolvedIntegration = resolvedIntegration(
                Set.of("git.pull_requests"),
                "active",
                "active",
                "github_app_private_key");
        integrationConfigClient.credentialLease = new CredentialLease(
                LEASE_ID,
                "github_app_private_key",
                leasedCredential,
                LEASE_EXPIRES_AT);
        integrationConfigClient.storedSecretRef = "vault://internal/github/api-token";
        integrationConfigClient.storedRawSecret = "stored-provider-credential";
        GitProviderActionService service = new GitProviderActionService(integrationConfigClient, providerActionPort);

        service.openPullRequest(openPullRequestCommand());

        assertEquals(List.of(new ResolveCall(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", "git.pull_requests")),
                integrationConfigClient.resolveCalls);
        assertEquals(List.of(new LeaseCall(TENANT_ID, ORG_ID, CONNECTION_ID, "github_app_private_key", "git-integration")),
                integrationConfigClient.leaseCalls);
        assertEquals(1, providerActionPort.actions.size());

        GitProviderAction action = providerActionPort.actions.get(0);
        assertEquals(GitProviderActionType.OPEN_PULL_REQUEST, action.type());
        assertEquals(CONNECTION_ID, action.connectionId());
        assertEquals("git.pull_requests", action.requiredCapability());
        assertEquals("github_app_private_key", action.credentialKind());
        assertArrayEquals(leasedCredential, action.credentialLease().secret());
        assertEquals("Update coverage", action.details().get("title"));

        leasedCredential[0] = 'X';
        assertEquals('l', action.credentialLease().secret()[0]);
        char[] returnedCredential = action.credentialLease().secret();
        returnedCredential[0] = 'Y';
        assertEquals('l', action.credentialLease().secret()[0]);

        String renderedAction = action.toString();
        assertFalse(renderedAction.contains(integrationConfigClient.storedSecretRef));
        assertFalse(renderedAction.contains(integrationConfigClient.storedRawSecret));
        assertFalse(renderedAction.contains("lease-value"));
        assertFalse(action.details().toString().contains(integrationConfigClient.storedSecretRef));
        assertFalse(action.details().toString().contains(integrationConfigClient.storedRawSecret));
    }

    @Test
    void branchCreationRequiresPullRequestCapabilityAndPassesBaseSha() {
        RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
        integrationConfigClient.resolvedIntegration = resolvedIntegration(Set.of("git.pull_requests"));
        integrationConfigClient.credentialLease = new CredentialLease(
                LEASE_ID,
                "api_token",
                "token".toCharArray(),
                LEASE_EXPIRES_AT);
        GitProviderActionService service = new GitProviderActionService(integrationConfigClient, providerActionPort);

        service.createBranch(new CreateBranchCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                "vericov/add-tests",
                "abc123",
                "branch-vericov-add-tests"));

        GitProviderAction action = providerActionPort.actions.get(0);
        assertEquals(GitProviderActionType.CREATE_BRANCH, action.type());
        assertEquals("git.pull_requests", action.requiredCapability());
        assertEquals("abc123", action.details().get("base_sha"));
    }

    @Test
    void checkRunCarriesAnnotationsAndDetailsUrl() {
        GitAnnotationInput annotation = new GitAnnotationInput(
                "src/App.java",
                10,
                12,
                "warning",
                "Changed branch is uncovered");

        CreateOrUpdateCheckRunCommand command = new CreateOrUpdateCheckRunCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                "Vericov Coverage",
                "abc123",
                "completed",
                "failure",
                "Patch coverage failed",
                "Line coverage dropped below threshold",
                "https://app.vericov.dev/reports/1",
                List.of(annotation),
                "check-abc123-coverage");

        assertEquals(List.of(annotation), command.annotations());
        assertEquals("https://app.vericov.dev/reports/1", command.detailsUrl());
    }

    @Test
    void checkRunDoesNotCallProviderWhenExistingIdempotencyKeyIsCompleted() {
        RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
        InMemoryGitActionRepository actionRepository = new InMemoryGitActionRepository();
        integrationConfigClient.resolvedIntegration = resolvedIntegration(Set.of("git.checks"));
        actionRepository.saveCheckRun(new GitCheckRunDetails(
                UUID.randomUUID(),
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                "abc123",
                "coverage",
                "provider-check-1",
                "completed",
                "success",
                "https://app.vericov.dev/report",
                Map.of("summary", "ok"),
                "coverage-abc123",
                Instant.now(),
                Instant.now()));
        GitProviderActionService service = new GitProviderActionService(
                integrationConfigClient,
                providerActionPort,
                actionRepository);

        service.createOrUpdateCheckRun(checkRunCommandWithIdempotencyKey("coverage-abc123"));

        assertTrue(providerActionPort.actions.isEmpty());
    }

    private static CreateOrUpdateCheckRunCommand checkRunCommand() {
        return new CreateOrUpdateCheckRunCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                "coverage",
                "abc123",
                "completed",
                "success");
    }

    private static CreateOrUpdateCheckRunCommand checkRunCommandWithIdempotencyKey(String idempotencyKey) {
        return new CreateOrUpdateCheckRunCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                "coverage",
                "abc123",
                "completed",
                "success",
                "Patch coverage passed",
                "Coverage passed",
                "https://app.vericov.dev/report",
                List.of(),
                idempotencyKey);
    }

    private static CreateOrUpdatePrCommentCommand commentCommand() {
        return new CreateOrUpdatePrCommentCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                12,
                "coverage-report",
                "Coverage report updated");
    }

    private static OpenPullRequestCommand openPullRequestCommand() {
        return new OpenPullRequestCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                "feature/coverage",
                "main",
                "Update coverage",
                "Refresh coverage artifacts");
    }

    private static ResolvedGitIntegration resolvedIntegration(Set<String> capabilities) {
        return resolvedIntegration(capabilities, "active", "active", "api_token");
    }

    private static ResolvedGitIntegration resolvedIntegration(
            Set<String> capabilities,
            String connectionStatus,
            String bindingStatus) {
        return resolvedIntegration(capabilities, connectionStatus, bindingStatus, "api_token");
    }

    private static ResolvedGitIntegration resolvedIntegration(
            Set<String> capabilities,
            String connectionStatus,
            String bindingStatus,
            String credentialKind) {
        return new ResolvedGitIntegration(
                TENANT_ID,
                ORG_ID,
                CONNECTION_ID,
                REPOSITORY_ID,
                "github",
                connectionStatus,
                bindingStatus,
                "vericov/vericov",
                credentialKind,
                capabilities);
    }

    private static final class RecordingIntegrationConfigClient implements IntegrationConfigClient {
        private final List<ResolveCall> resolveCalls = new ArrayList<>();
        private final List<LeaseCall> leaseCalls = new ArrayList<>();
        private ResolvedGitIntegration resolvedIntegration;
        private CredentialLease credentialLease;
        private RuntimeException resolveFailure;
        private String storedSecretRef;
        private String storedRawSecret;

        @Override
        public ResolvedGitIntegration resolveRepositoryIntegration(
                UUID tenantId,
                UUID orgId,
                UUID repositoryId,
                String providerKey,
                String capability) {
            resolveCalls.add(new ResolveCall(tenantId, orgId, repositoryId, providerKey, capability));
            if (resolveFailure != null) {
                throw resolveFailure;
            }
            return resolvedIntegration;
        }

        @Override
        public CredentialLease leaseCredential(
                UUID tenantId,
                UUID orgId,
                UUID connectionId,
                String credentialKind,
                String serviceName) {
            leaseCalls.add(new LeaseCall(tenantId, orgId, connectionId, credentialKind, serviceName));
            return credentialLease;
        }
    }

    private static final class RecordingGitProviderActionPort implements GitProviderActionPort {
        private final List<GitProviderAction> actions = new ArrayList<>();

        @Override
        public GitProviderActionResult execute(GitProviderAction action) {
            actions.add(action);
            return new GitProviderActionResult(
                    action.type(),
                    "provider-id",
                    action.type() == GitProviderActionType.CREATE_BRANCH ? "created" : "completed",
                    "https://provider.example/action",
                    Map.of());
        }
    }

    private record ResolveCall(
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String capability) {
    }

    private record LeaseCall(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind,
            String serviceName) {
    }
}
