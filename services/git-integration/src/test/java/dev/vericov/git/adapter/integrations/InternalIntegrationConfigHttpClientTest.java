package dev.vericov.git.adapter.integrations;

import dev.vericov.git.application.port.ResolvedGitIntegration;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalIntegrationConfigHttpClientTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BINDING_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void resolveRepositoryIntegrationSendsServiceIdentityAndParsesCredentialKind() {
        RecordingHttpTransport transport = new RecordingHttpTransport("""
                {"data":{"connection":{"id":"44444444-4444-4444-4444-444444444444","tenant_id":"11111111-1111-1111-1111-111111111111","org_id":"22222222-2222-2222-2222-222222222222","provider_key":"github","integration_type":"git","display_name":"GitHub","external_account_id":"vericov/vericov","external_account_name":"Vericov","status":"active","config":{"installation_id":"123456"}},"binding":{"id":"55555555-5555-5555-5555-555555555555","tenant_id":"11111111-1111-1111-1111-111111111111","connection_id":"44444444-4444-4444-4444-444444444444","scope_type":"repository","scope_id":"33333333-3333-3333-3333-333333333333","capabilities":["git.checks"],"config":{},"status":"active"},"credential_kind":"github_app_private_key"}}
                """);
        InternalIntegrationConfigHttpClient client = new InternalIntegrationConfigHttpClient(
                URI.create("http://integrations:8084"),
                "git-integration",
                "service-token",
                transport);

        ResolvedGitIntegration resolved = client.resolveRepositoryIntegration(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "github",
                "git.checks");

        assertEquals(CONNECTION_ID, resolved.connectionId());
        assertEquals("github_app_private_key", resolved.credentialKind());
        assertEquals("git-integration", transport.lastRequest.headers().firstValue("X-Vericov-Service-Name").orElseThrow());
        assertEquals("service-token", transport.lastRequest.headers().firstValue("X-Vericov-Service-Token").orElseThrow());
        assertEquals(BINDING_ID, UUID.fromString(transport.lastBindingId));
    }

    private static final class RecordingHttpTransport implements InternalIntegrationConfigHttpClient.HttpTransport {
        private final String body;
        private HttpRequest lastRequest;
        private String lastBindingId;

        private RecordingHttpTransport(String body) {
            this.body = body;
        }

        @Override
        public InternalIntegrationConfigHttpClient.HttpResult send(HttpRequest request) {
            lastRequest = request;
            lastBindingId = BINDING_ID.toString();
            return new InternalIntegrationConfigHttpClient.HttpResult(200, body);
        }
    }
}
