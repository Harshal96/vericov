package dev.vericov.integrations.api;

import dev.vericov.integrations.application.CredentialLease;
import dev.vericov.integrations.application.InMemoryIntegrationRepository;
import dev.vericov.integrations.application.IntegrationApplicationService;
import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.port.CredentialVault;
import dev.vericov.integrations.application.port.IntegrationAuthorizer;
import dev.vericov.integrations.application.port.IntegrationScopeValidator;
import dev.vericov.integrations.config.StaticProviderRegistry;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationResourceTest {
    private static final UUID REQUESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REPOSITORY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");

    @Test
    void listsGitProvidersEnvelope() {
        IntegrationResource resource = resource();

        Response response = resource.listProviders("git");

        assertEquals(200, response.getStatus());
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        List<?> providers = assertInstanceOf(List.class, envelope.data());
        assertEquals(3, providers.size());
        assertProvider(providers.get(0), "github", "GitHub");
        assertProvider(providers.get(1), "gitlab", "GitLab");
        assertProvider(providers.get(2), "bitbucket", "Bitbucket");
    }

    @Test
    void createsConnectionEnvelope() {
        IntegrationResource resource = resource();

        Response response = resource.createConnection(
                REQUESTER_ID.toString(),
                ORG_ID.toString(),
                createGithubRequest("123456"));

        assertEquals(201, response.getStatus());
        IntegrationConnectionHttpResponse body = responseBody(response, IntegrationConnectionHttpResponse.class);
        assertEquals(TENANT_ID, body.tenantId());
        assertEquals(ORG_ID, body.orgId());
        assertEquals("github", body.providerKey());
        assertEquals("git", body.integrationType());
        assertEquals("Engineering GitHub", body.displayName());
        assertEquals("123456", body.externalAccountId());
        assertEquals("active", body.status());
        assertEquals(
                "/api/v1/integrations/" + body.id() + "?tenant_id=" + TENANT_ID + "&org_id=" + ORG_ID,
                response.getLocation().toString());
    }

    @Test
    void duplicateConnectionReturnsConflictEnvelope() {
        IntegrationResource resource = resource();
        resource.createConnection(
                REQUESTER_ID.toString(),
                ORG_ID.toString(),
                createGithubRequest("123456"));

        Response response = resource.createConnection(
                REQUESTER_ID.toString(),
                ORG_ID.toString(),
                createGithubRequest("123456"));

        assertEquals(409, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("conflict", error.error().code());
    }

    @Test
    void listsAndGetsConnectionsEnvelope() {
        IntegrationResource resource = resource();
        IntegrationConnectionHttpResponse created = createConnection(resource);

        Response listResponse = resource.listConnections(
                REQUESTER_ID.toString(),
                ORG_ID.toString(),
                TENANT_ID.toString());
        Response getResponse = resource.getConnection(
                REQUESTER_ID.toString(),
                created.id().toString(),
                TENANT_ID.toString(),
                ORG_ID.toString());

        assertEquals(200, listResponse.getStatus());
        List<?> connections = assertInstanceOf(List.class, responseEnvelope(listResponse).data());
        assertEquals(1, connections.size());
        assertEquals(created.id(), assertInstanceOf(IntegrationConnectionHttpResponse.class, connections.getFirst()).id());
        assertEquals(200, getResponse.getStatus());
        assertEquals(created.id(), responseBody(getResponse, IntegrationConnectionHttpResponse.class).id());
    }

    @Test
    void createdLocationIsDereferenceableWithQueryScope() {
        IntegrationResource resource = resource();
        Response createResponse = resource.createConnection(
                REQUESTER_ID.toString(),
                ORG_ID.toString(),
                createGithubRequest("123456"));
        IntegrationConnectionHttpResponse created = responseBody(createResponse, IntegrationConnectionHttpResponse.class);
        URI location = createResponse.getLocation();

        Response getResponse = resource.getConnection(
                REQUESTER_ID.toString(),
                location.getPath().substring(location.getPath().lastIndexOf('/') + 1),
                queryParam(location, "tenant_id"),
                queryParam(location, "org_id"));

        assertEquals(created.id(), responseBody(getResponse, IntegrationConnectionHttpResponse.class).id());
    }

    @Test
    void upsertsRepositoryBindingEnvelope() {
        IntegrationResource resource = resource();
        IntegrationConnectionHttpResponse connection = createConnection(resource);

        Response response = resource.upsertBinding(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                "repository",
                REPOSITORY_ID.toString(),
                new UpsertIntegrationBindingHttpRequest(
                        TENANT_ID,
                        ORG_ID,
                        List.of("git.checks", "git.comments"),
                        Map.of("events", List.of("pull_request")),
                        "active",
                        null));

        assertEquals(200, response.getStatus());
        IntegrationBindingHttpResponse body = responseBody(response, IntegrationBindingHttpResponse.class);
        assertEquals(connection.id(), body.connectionId());
        assertEquals("repository", body.scopeType());
        assertEquals(REPOSITORY_ID, body.scopeId());
        assertEquals(List.of("git.checks", "git.comments"), body.capabilities());
        assertEquals("active", body.status());
    }

    @Test
    void createsCredentialWithoutEchoingSecret() {
        IntegrationResource resource = resource();
        IntegrationConnectionHttpResponse connection = createConnection(resource);

        Response response = resource.createCredential(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                new CreateIntegrationCredentialHttpRequest(
                        TENANT_ID,
                        ORG_ID,
                        "github_app_private_key",
                        "private-key-value".toCharArray(),
                        null));

        assertEquals(201, response.getStatus());
        IntegrationCredentialHttpResponse body = responseBody(response, IntegrationCredentialHttpResponse.class);
        assertEquals(connection.id(), body.connectionId());
        assertEquals("github_app_private_key", body.credentialKind());
        assertEquals("active", body.status());
        assertTrue(body.secretRef().startsWith("vault://test/"));
    }

    @Test
    void createsWebhookEndpointMetadata() {
        IntegrationResource resource = resource();
        IntegrationConnectionHttpResponse connection = createConnection(resource);

        Response response = resource.createWebhookEndpoint(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                new CreateIntegrationWebhookEndpointHttpRequest(
                        TENANT_ID,
                        ORG_ID,
                        "github",
                        "external-hook-1",
                        "https://api.vericov.dev/webhooks/github",
                        List.of("pull_request", "check_run"),
                        "vault://test/webhook-secret",
                        Map.of("content_type", "json")));

        assertEquals(201, response.getStatus());
        IntegrationWebhookEndpointHttpResponse body = responseBody(response, IntegrationWebhookEndpointHttpResponse.class);
        assertEquals(connection.id(), body.connectionId());
        assertEquals("github", body.providerKey());
        assertEquals(List.of("pull_request", "check_run"), body.eventTypes());
        assertEquals("active", body.status());
    }

    @Test
    void listsAndDisablesBindingEnvelopeWithoutDeleteBody() {
        IntegrationResource resource = resource();
        IntegrationConnectionHttpResponse connection = createConnection(resource);
        IntegrationBindingHttpResponse binding = upsertRepositoryBinding(resource, connection);

        Response listResponse = resource.listBindings(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                TENANT_ID.toString(),
                ORG_ID.toString());
        Response disableResponse = resource.disableBinding(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                "repository",
                REPOSITORY_ID.toString(),
                TENANT_ID.toString(),
                ORG_ID.toString(),
                binding.updatedAt().toString());

        assertEquals(200, listResponse.getStatus());
        List<?> bindings = assertInstanceOf(List.class, responseEnvelope(listResponse).data());
        assertEquals(1, bindings.size());
        assertEquals(binding.id(), assertInstanceOf(IntegrationBindingHttpResponse.class, bindings.getFirst()).id());
        assertEquals(200, disableResponse.getStatus());
        assertEquals("disabled", responseBody(disableResponse, IntegrationBindingHttpResponse.class).status());
    }

    @Test
    void updatesAndDisablesConnectionEnvelope() {
        IntegrationResource resource = resource();
        IntegrationConnectionHttpResponse connection = createConnection(resource);

        Response updateResponse = resource.updateConnection(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                new UpdateIntegrationConnectionHttpRequest(
                        TENANT_ID,
                        ORG_ID,
                        "Primary GitHub",
                        "active",
                        Map.of("installation_id", "654321"),
                        connection.updatedAt()));

        assertEquals(200, updateResponse.getStatus());
        IntegrationConnectionHttpResponse updated = responseBody(updateResponse, IntegrationConnectionHttpResponse.class);
        assertEquals("Primary GitHub", updated.displayName());
        assertEquals("654321", updated.config().get("installation_id"));

        Response disableResponse = resource.disableConnection(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                new DisableIntegrationConnectionHttpRequest(TENANT_ID, ORG_ID, updated.updatedAt()));

        assertEquals(200, disableResponse.getStatus());
        IntegrationConnectionHttpResponse disabled = responseBody(disableResponse, IntegrationConnectionHttpResponse.class);
        assertEquals("disabled", disabled.status());
    }

    @Test
    void mapsAuthenticationAuthorizationNotFoundAndValidationErrors() {
        IntegrationResource resource = resource();
        IntegrationConnectionHttpResponse connection = createConnection(resource);
        IntegrationResource denied = resource((requesterUserId, tenantId, orgId, action) -> {
            throw new IntegrationException("forbidden", "Integration API access denied");
        });

        assertError(
                resource.listConnections(null, ORG_ID.toString(), TENANT_ID.toString()),
                401,
                "unauthorized");
        assertError(
                resource.listConnections("not-a-uuid", ORG_ID.toString(), TENANT_ID.toString()),
                401,
                "unauthorized");
        assertError(
                denied.listConnections(REQUESTER_ID.toString(), ORG_ID.toString(), TENANT_ID.toString()),
                403,
                "forbidden");
        assertError(
                resource.getConnection(
                        REQUESTER_ID.toString(),
                        UUID.randomUUID().toString(),
                        TENANT_ID.toString(),
                        ORG_ID.toString()),
                404,
                "not_found");
        assertError(
                resource.listConnections(REQUESTER_ID.toString(), "not-a-uuid", TENANT_ID.toString()),
                400,
                "validation_error");
        assertError(
                resource.upsertBinding(
                        REQUESTER_ID.toString(),
                        connection.id().toString(),
                        "repository",
                        "not-a-uuid",
                        new UpsertIntegrationBindingHttpRequest(
                                TENANT_ID,
                                ORG_ID,
                                List.of("git.checks"),
                                Map.of(),
                                "active",
                                null)),
                400,
                "validation_error");
        assertError(
                resource.getConnection(
                        REQUESTER_ID.toString(),
                        connection.id().toString(),
                        "not-a-uuid",
                        ORG_ID.toString()),
                400,
                "validation_error");
        assertError(
                resource.disableBinding(
                        REQUESTER_ID.toString(),
                        connection.id().toString(),
                        "repository",
                        REPOSITORY_ID.toString(),
                        TENANT_ID.toString(),
                        ORG_ID.toString(),
                        "not-an-instant"),
                400,
                "validation_error");
    }

    @Test
    void registersIntegrationResourceInApplication() {
        assertEquals(
                true,
                new IntegrationsApplication().getClasses().contains(IntegrationResource.class));
    }

    private static IntegrationResource resource() {
        return resource(new TupleCheckingAuthorizer(Set.of(new OrgAccessGrant(REQUESTER_ID, TENANT_ID, ORG_ID))));
    }

    private static IntegrationResource resource(IntegrationAuthorizer authorizer) {
        StaticProviderRegistry registry = StaticProviderRegistry.defaultRegistry();
        IntegrationApplicationService service = new IntegrationApplicationService(
                new InMemoryIntegrationRepository(),
                registry,
                new TestCredentialVault(Clock.fixed(NOW, ZoneOffset.UTC)),
                new TestScopeValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new IntegrationResource(service, registry, authorizer);
    }

    private static CreateIntegrationConnectionHttpRequest createGithubRequest(String externalAccountId) {
        return new CreateIntegrationConnectionHttpRequest(
                TENANT_ID,
                "github",
                "Engineering GitHub",
                externalAccountId,
                "Vericov",
                Map.of("installation_id", externalAccountId));
    }

    private static IntegrationConnectionHttpResponse createConnection(IntegrationResource resource) {
        return responseBody(
                resource.createConnection(
                        REQUESTER_ID.toString(),
                        ORG_ID.toString(),
                        createGithubRequest(UUID.randomUUID().toString())),
                IntegrationConnectionHttpResponse.class);
    }

    private static IntegrationBindingHttpResponse upsertRepositoryBinding(
            IntegrationResource resource,
            IntegrationConnectionHttpResponse connection) {
        return responseBody(resource.upsertBinding(
                REQUESTER_ID.toString(),
                connection.id().toString(),
                "repository",
                REPOSITORY_ID.toString(),
                new UpsertIntegrationBindingHttpRequest(
                        TENANT_ID,
                        ORG_ID,
                        List.of("git.checks"),
                        Map.of(),
                        "active",
                        null)), IntegrationBindingHttpResponse.class);
    }

    private static void assertProvider(Object value, String providerKey, String displayName) {
        ProviderDefinitionHttpResponse provider = assertInstanceOf(ProviderDefinitionHttpResponse.class, value);
        assertEquals(providerKey, provider.providerKey());
        assertEquals(displayName, provider.displayName());
        assertEquals("git", provider.type());
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        return assertInstanceOf(type, responseEnvelope(response).data());
    }

    private static ApiResponse<?> responseEnvelope(Response response) {
        return assertInstanceOf(ApiResponse.class, response.getEntity());
    }

    private static void assertError(Response response, int status, String code) {
        assertEquals(status, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals(code, error.error().code());
    }

    private static String queryParam(URI uri, String name) {
        for (String pair : uri.getQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return parts[1];
            }
        }
        throw new IllegalArgumentException("Missing query parameter: " + name);
    }

    private record OrgAccessGrant(UUID requesterUserId, UUID tenantId, UUID orgId) {
    }

    private record TupleCheckingAuthorizer(Set<OrgAccessGrant> grants) implements IntegrationAuthorizer {
        private TupleCheckingAuthorizer {
            grants = Set.copyOf(grants);
        }

        @Override
        public void requireOrgAccess(UUID requesterUserId, UUID tenantId, UUID orgId, String action) {
            if (!grants.contains(new OrgAccessGrant(requesterUserId, tenantId, orgId))) {
                throw new IntegrationException("forbidden", "Integration API access denied");
            }
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
