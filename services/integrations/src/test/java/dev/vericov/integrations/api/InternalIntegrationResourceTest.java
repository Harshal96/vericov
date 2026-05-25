package dev.vericov.integrations.api;

import dev.vericov.integrations.application.CreateCredentialCommand;
import dev.vericov.integrations.application.CredentialLease;
import dev.vericov.integrations.application.InMemoryIntegrationRepository;
import dev.vericov.integrations.application.IntegrationApplicationService;
import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.port.CredentialVault;
import dev.vericov.integrations.application.port.InternalServiceAuthorizer;
import dev.vericov.integrations.application.port.IntegrationScopeValidator;
import dev.vericov.integrations.config.StaticProviderRegistry;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalIntegrationResourceTest {
    private static final UUID REQUESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REPOSITORY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");
    private static final String SERVICE_NAME = "git-checks-service";
    private static final String SERVICE_TOKEN = "proof-token";
    private static final String RAW_SECRET = "internal-github-token";

    @Test
    void resolvesActiveGithubRepositoryBindingByProviderScopeAndCapability() {
        TestFixture fixture = new TestFixture();

        Response response = fixture.resource.resolve(
                SERVICE_NAME,
                SERVICE_TOKEN,
                TENANT_ID.toString(),
                ORG_ID.toString(),
                "github",
                "repository",
                REPOSITORY_ID.toString(),
                "git.checks");

        assertEquals(200, response.getStatus());
        ResolvedIntegrationHttpResponse body = responseBody(response, ResolvedIntegrationHttpResponse.class);
        assertEquals(fixture.connection.id(), body.connection().id());
        assertEquals(fixture.binding.id(), body.binding().id());
        assertEquals("github_app_private_key", body.credentialKind());
    }

    @Test
    void resolveReturnsNotFoundWhenRequiredCapabilityIsMissing() {
        TestFixture fixture = new TestFixture();

        Response response = fixture.resource.resolve(
                SERVICE_NAME,
                SERVICE_TOKEN,
                TENANT_ID.toString(),
                ORG_ID.toString(),
                "github",
                "repository",
                REPOSITORY_ID.toString(),
                "git.comments");

        assertError(response, 404, "not_found");
    }

    @Test
    void credentialLeaseRequiresServiceIdentityAndProofToken() {
        TestFixture fixture = new TestFixture();
        CreateCredentialLeaseHttpRequest request = leaseRequest("api_token");

        assertError(
                fixture.resource.createCredentialLease(
                        null,
                        SERVICE_TOKEN,
                        fixture.connection.id().toString(),
                        request),
                401,
                "unauthorized");
        assertError(
                fixture.resource.createCredentialLease(
                        " ",
                        SERVICE_TOKEN,
                        fixture.connection.id().toString(),
                        request),
                401,
                "unauthorized");
        assertError(
                fixture.resource.createCredentialLease(
                        SERVICE_NAME,
                        null,
                        fixture.connection.id().toString(),
                        request),
                401,
                "unauthorized");
        assertError(
                fixture.resource.createCredentialLease(
                        SERVICE_NAME,
                        "wrong-token",
                        fixture.connection.id().toString(),
                        request),
                401,
                "unauthorized");
    }

    @Test
    void credentialLeaseReturnsSecretButDoesNotLeakThroughToStringOrErrors() {
        TestFixture fixture = new TestFixture();

        Response response = fixture.resource.createCredentialLease(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                leaseRequest("api_token"));
        CredentialLeaseHttpResponse body = responseBody(response, CredentialLeaseHttpResponse.class);

        assertEquals(200, response.getStatus());
        assertTrue(body.secretRef().startsWith("vault://test/"));
        assertEquals(RAW_SECRET, body.secret());
        assertEquals(NOW.plusSeconds(300), body.expiresAt());
        assertFalse(body.toString().contains(RAW_SECRET));

        Response errorResponse = fixture.resource.createCredentialLease(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                leaseRequest("webhook_secret"));
        assertError(errorResponse, 404, "not_found");
        assertFalse(errorResponse.getEntity().toString().contains(RAW_SECRET));
    }

    @Test
    void internalEndpointsRequireServiceIdentity() {
        TestFixture fixture = new TestFixture();

        assertError(fixture.resource.getConnection(
                null,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                TENANT_ID.toString(),
                ORG_ID.toString()), 401, "unauthorized");
        assertError(fixture.resource.getConnection(
                " ",
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                TENANT_ID.toString(),
                ORG_ID.toString()), 401, "unauthorized");
        assertError(fixture.resource.getConnection(
                SERVICE_NAME,
                "bad-token",
                fixture.connection.id().toString(),
                TENANT_ID.toString(),
                ORG_ID.toString()), 401, "unauthorized");
        assertError(fixture.resource.resolve(
                null,
                SERVICE_TOKEN,
                TENANT_ID.toString(),
                ORG_ID.toString(),
                "github",
                "repository",
                REPOSITORY_ID.toString(),
                "git.checks"), 401, "unauthorized");
        assertError(fixture.resource.resolve(
                " ",
                SERVICE_TOKEN,
                TENANT_ID.toString(),
                ORG_ID.toString(),
                "github",
                "repository",
                REPOSITORY_ID.toString(),
                "git.checks"), 401, "unauthorized");
        assertError(fixture.resource.resolve(
                SERVICE_NAME,
                "bad-token",
                TENANT_ID.toString(),
                ORG_ID.toString(),
                "github",
                "repository",
                REPOSITORY_ID.toString(),
                "git.checks"), 401, "unauthorized");
        assertError(fixture.resource.acceptSyncState(
                null,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                null), 401, "unauthorized");
        assertError(fixture.resource.acceptSyncState(
                " ",
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                null), 401, "unauthorized");
        assertError(fixture.resource.acceptSyncState(
                SERVICE_NAME,
                "bad-token",
                fixture.connection.id().toString(),
                null), 401, "unauthorized");
        assertError(fixture.resource.acceptEvent(
                null,
                SERVICE_TOKEN,
                null), 401, "unauthorized");
        assertError(fixture.resource.acceptEvent(
                " ",
                SERVICE_TOKEN,
                null), 401, "unauthorized");
        assertError(fixture.resource.acceptEvent(
                SERVICE_NAME,
                "bad-token",
                null), 401, "unauthorized");
    }

    @Test
    void internalEndpointsReturnValidationErrorForMalformedIds() {
        TestFixture fixture = new TestFixture();

        assertError(fixture.resource.getConnection(
                SERVICE_NAME,
                SERVICE_TOKEN,
                "not-a-uuid",
                TENANT_ID.toString(),
                ORG_ID.toString()), 400, "validation_error");
        assertError(fixture.resource.getConnection(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                "not-a-uuid",
                ORG_ID.toString()), 400, "validation_error");
        assertError(fixture.resource.getConnection(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                TENANT_ID.toString(),
                "not-a-uuid"), 400, "validation_error");
        assertError(fixture.resource.resolve(
                SERVICE_NAME,
                SERVICE_TOKEN,
                "not-a-uuid",
                ORG_ID.toString(),
                "github",
                "repository",
                REPOSITORY_ID.toString(),
                "git.checks"), 400, "validation_error");
        assertError(fixture.resource.resolve(
                SERVICE_NAME,
                SERVICE_TOKEN,
                TENANT_ID.toString(),
                "not-a-uuid",
                "github",
                "repository",
                REPOSITORY_ID.toString(),
                "git.checks"), 400, "validation_error");
        assertError(fixture.resource.resolve(
                SERVICE_NAME,
                SERVICE_TOKEN,
                TENANT_ID.toString(),
                ORG_ID.toString(),
                "github",
                "repository",
                "not-a-uuid",
                "git.checks"), 400, "validation_error");
        assertError(fixture.resource.createCredentialLease(
                SERVICE_NAME,
                SERVICE_TOKEN,
                "not-a-uuid",
                leaseRequest("api_token")), 400, "validation_error");
        assertError(fixture.resource.createCredentialLease(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                new CreateCredentialLeaseHttpRequest("not-a-uuid", ORG_ID.toString(), "api_token")),
                400,
                "validation_error");
        assertError(fixture.resource.createCredentialLease(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                new CreateCredentialLeaseHttpRequest(TENANT_ID.toString(), "not-a-uuid", "api_token")),
                400,
                "validation_error");
        assertError(fixture.resource.acceptSyncState(
                SERVICE_NAME,
                SERVICE_TOKEN,
                "not-a-uuid",
                null), 400, "validation_error");
        assertError(fixture.resource.acceptSyncState(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                new UpdateIntegrationSyncStateHttpRequest(
                        "not-a-uuid",
                        ORG_ID.toString(),
                        "repository_full",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "running",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        null,
                        null,
                        null,
                        null)), 400, "validation_error");
        assertError(fixture.resource.acceptSyncState(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                new UpdateIntegrationSyncStateHttpRequest(
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        "repository_full",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "running",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        "not-an-instant",
                        null,
                        null,
                        null)), 400, "validation_error");
        assertError(fixture.resource.acceptEvent(
                SERVICE_NAME,
                SERVICE_TOKEN,
                new RecordIntegrationEventHttpRequest(
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        "not-a-uuid",
                        "github",
                        "sync.started",
                        "evt-1",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "processed",
                        Map.of(),
                        Map.of(),
                        null,
                        null)), 400, "validation_error");
        assertError(fixture.resource.acceptEvent(
                SERVICE_NAME,
                SERVICE_TOKEN,
                new RecordIntegrationEventHttpRequest(
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        fixture.connection.id().toString(),
                        "github",
                        "sync.started",
                        "evt-1",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "processed",
                        Map.of(),
                        Map.of(),
                        "not-an-instant",
                        null)), 400, "validation_error");
    }

    @Test
    void getsInternalConnectionByScopedIds() {
        TestFixture fixture = new TestFixture();

        Response response = fixture.resource.getConnection(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                TENANT_ID.toString(),
                ORG_ID.toString());

        assertEquals(200, response.getStatus());
        IntegrationConnectionHttpResponse body = responseBody(response, IntegrationConnectionHttpResponse.class);
        assertEquals(fixture.connection.id(), body.id());
        assertEquals("github", body.providerKey());
    }

    @Test
    void internalEndpointsPersistSyncStateAndEvents() {
        TestFixture fixture = new TestFixture();

        Response syncResponse = fixture.resource.acceptSyncState(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                new UpdateIntegrationSyncStateHttpRequest(
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        "repository_full",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "running",
                        Map.of("cursor", "next"),
                        Map.of("checkpoint", 42),
                        Map.of(),
                        NOW.minusSeconds(60).toString(),
                        null,
                        NOW.plusSeconds(300).toString(),
                        NOW.plusSeconds(120).toString()));
        Response eventResponse = fixture.resource.acceptEvent(
                SERVICE_NAME,
                SERVICE_TOKEN,
                new RecordIntegrationEventHttpRequest(
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        fixture.connection.id().toString(),
                        "github",
                        "sync.started",
                        "evt-1",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "processed",
                        Map.of("cursor", "next"),
                        Map.of(),
                        NOW.toString(),
                        NOW.plusSeconds(1).toString()));

        IntegrationSyncStateHttpResponse syncBody = acceptedBody(syncResponse, IntegrationSyncStateHttpResponse.class);
        IntegrationEventHttpResponse eventBody = acceptedBody(eventResponse, IntegrationEventHttpResponse.class);
        assertEquals("repository_full", syncBody.syncType());
        assertEquals("running", syncBody.status());
        assertEquals(Map.of("cursor", "next"), syncBody.cursor());
        assertEquals("sync.started", eventBody.eventType());
        assertEquals("github", eventBody.providerKey());
        assertEquals(Map.of("cursor", "next"), eventBody.payload());
    }

    @Test
    void internalSyncAndEventPayloadsRejectSecretBearingKeys() {
        TestFixture fixture = new TestFixture();

        assertError(fixture.resource.acceptSyncState(
                SERVICE_NAME,
                SERVICE_TOKEN,
                fixture.connection.id().toString(),
                new UpdateIntegrationSyncStateHttpRequest(
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        "repository_full",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "running",
                        Map.of("provider", Map.of("access_token", "do-not-store")),
                        Map.of(),
                        Map.of(),
                        null,
                        null,
                        null,
                        null)), 400, "validation_error");
        assertError(fixture.resource.acceptEvent(
                SERVICE_NAME,
                SERVICE_TOKEN,
                new RecordIntegrationEventHttpRequest(
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        fixture.connection.id().toString(),
                        "github",
                        "sync.started",
                        "evt-secret",
                        "repository",
                        REPOSITORY_ID.toString(),
                        "processed",
                        Map.of("provider", List.of(Map.of("private_key", "do-not-store"))),
                        Map.of(),
                        null,
                        null)), 400, "validation_error");
    }

    @Test
    void registersInternalResourceInApplication() {
        assertTrue(new IntegrationsApplication().getClasses().contains(InternalIntegrationResource.class));
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        return assertInstanceOf(type, responseEnvelope(response).data());
    }

    private static ApiResponse<?> responseEnvelope(Response response) {
        return assertInstanceOf(ApiResponse.class, response.getEntity());
    }

    private static <T> T acceptedBody(Response response, Class<T> type) {
        assertEquals(202, response.getStatus());
        return assertInstanceOf(type, responseEnvelope(response).data());
    }

    private static void assertError(Response response, int status, String code) {
        assertEquals(status, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals(code, error.error().code());
    }

    private static CreateCredentialLeaseHttpRequest leaseRequest(String credentialKind) {
        return new CreateCredentialLeaseHttpRequest(
                TENANT_ID.toString(),
                ORG_ID.toString(),
                credentialKind);
    }

    private static final class TestFixture {
        private final IntegrationApplicationService service;
        private final InternalIntegrationResource resource;
        private final IntegrationConnectionHttpResponse connection;
        private final IntegrationBindingHttpResponse binding;

        private TestFixture() {
            StaticProviderRegistry registry = StaticProviderRegistry.defaultRegistry();
            service = new IntegrationApplicationService(
                    new InMemoryIntegrationRepository(),
                    registry,
                    new TestCredentialVault(Clock.fixed(NOW, ZoneOffset.UTC)),
                    new TestScopeValidator(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
            IntegrationResource publicResource = new IntegrationResource(
                    service,
                    registry,
                    (requesterUserId, tenantId, orgId, action) -> {
                    });
            resource = new InternalIntegrationResource(service, new TestInternalServiceAuthorizer());
            connection = responseBody(publicResource.createConnection(
                    REQUESTER_ID.toString(),
                    ORG_ID.toString(),
                    new CreateIntegrationConnectionHttpRequest(
                            TENANT_ID,
                            "github",
                            "Engineering GitHub",
                            "123456",
                            "Vericov",
                            Map.of("installation_id", "123456"))), IntegrationConnectionHttpResponse.class);
            binding = responseBody(publicResource.upsertBinding(
                    REQUESTER_ID.toString(),
                    connection.id().toString(),
                    "repository",
                    REPOSITORY_ID.toString(),
                    new UpsertIntegrationBindingHttpRequest(
                            TENANT_ID,
                            ORG_ID,
                            List.of("git.checks"),
                            Map.of("events", List.of("pull_request")),
                            "active",
                            null)), IntegrationBindingHttpResponse.class);
            service.createCredential(new CreateCredentialCommand(
                    REQUESTER_ID,
                    TENANT_ID,
                    ORG_ID,
                    connection.id(),
                    "api_token",
                    RAW_SECRET.toCharArray(),
                    NOW.plusSeconds(3600)));
            service.createCredential(new CreateCredentialCommand(
                    REQUESTER_ID,
                    TENANT_ID,
                    ORG_ID,
                    connection.id(),
                    "github_app_private_key",
                    "github-app-private-key".toCharArray(),
                    NOW.plusSeconds(3600)));
        }
    }

    private static final class TestInternalServiceAuthorizer implements InternalServiceAuthorizer {
        @Override
        public String requireAuthorizedService(String serviceName, String serviceToken) {
            if (serviceName == null || serviceName.isBlank()) {
                throw new IntegrationException("unauthorized", "Service identity is required");
            }
            if (!SERVICE_TOKEN.equals(serviceToken) || !SERVICE_NAME.equals(serviceName.trim())) {
                throw new IntegrationException("unauthorized", "Internal service authorization failed");
            }
            return serviceName.trim();
        }
    }

    private static final class TestScopeValidator implements IntegrationScopeValidator {
        @Override
        public void requireScope(UUID tenantId, UUID orgId, String scopeType, UUID scopeId) {
            if ("organization".equals(scopeType) && ORG_ID.equals(scopeId)) {
                return;
            }
            if ("repository".equals(scopeType) && REPOSITORY_ID.equals(scopeId)) {
                return;
            }
            throw new IntegrationException("not_found", "Integration scope not found");
        }
    }

    private static final class TestCredentialVault implements CredentialVault {
        private final Clock clock;
        private final Map<String, StoredSecret> secretsByRef = new HashMap<>();

        private TestCredentialVault(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized String store(UUID tenantId, UUID connectionId, String credentialKind, char[] secret) {
            String secretRef = "vault://test/" + UUID.randomUUID();
            secretsByRef.put(secretRef, new StoredSecret(
                    tenantId,
                    connectionId,
                    Arrays.copyOf(secret, secret.length)));
            return secretRef;
        }

        @Override
        public synchronized CredentialLease lease(UUID tenantId, UUID connectionId, String secretRef, String requestedBy) {
            StoredSecret stored = secretsByRef.get(secretRef);
            if (stored == null
                    || !stored.tenantId().equals(tenantId)
                    || !stored.connectionId().equals(connectionId)) {
                throw new IntegrationException("not_found", "Integration credential not found");
            }
            return new CredentialLease(secretRef, stored.secret(), clock.instant().plusSeconds(300));
        }

        @Override
        public synchronized void revoke(UUID tenantId, String secretRef) {
            StoredSecret stored = secretsByRef.get(secretRef);
            if (stored != null && stored.tenantId().equals(tenantId)) {
                secretsByRef.remove(secretRef);
            }
        }
    }

    private record StoredSecret(UUID tenantId, UUID connectionId, char[] secret) {
        private StoredSecret {
            secret = Arrays.copyOf(secret, secret.length);
        }

        @Override
        public char[] secret() {
            return Arrays.copyOf(secret, secret.length);
        }
    }
}
