package dev.vericov.git.adapter.integrations;

import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.port.CredentialLease;
import dev.vericov.git.application.port.IntegrationConfigClient;
import dev.vericov.git.application.port.ResolvedGitIntegration;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.annotation.JsonbProperty;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class InternalIntegrationConfigHttpClient implements IntegrationConfigClient {
    private final URI baseUri;
    private final String serviceName;
    private final String serviceToken;
    private final HttpTransport transport;
    private final Jsonb jsonb = JsonbBuilder.create();

    public InternalIntegrationConfigHttpClient(
            URI baseUri,
            String serviceName,
            String serviceToken) {
        this(baseUri, serviceName, serviceToken, new JavaHttpTransport(HttpClient.newHttpClient()));
    }

    InternalIntegrationConfigHttpClient(
            URI baseUri,
            String serviceName,
            String serviceToken,
            HttpTransport transport) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.serviceName = requireText(serviceName, "serviceName");
        this.serviceToken = requireText(serviceToken, "serviceToken");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public ResolvedGitIntegration resolveRepositoryIntegration(
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String capability) {
        URI uri = baseUri.resolve("/internal/v1/integrations/resolve"
                + "?tenant_id=" + encode(tenantId.toString())
                + "&org_id=" + encode(orgId.toString())
                + "&provider_key=" + encode(providerKey)
                + "&scope_type=repository"
                + "&scope_id=" + encode(repositoryId.toString())
                + "&capability=" + encode(capability));
        HttpResult result = send(HttpRequest.newBuilder(uri).GET());
        ResolveEnvelope envelope = jsonb.fromJson(result.body(), ResolveEnvelope.class);
        if (envelope == null || envelope.data() == null) {
            throw new GitIntegrationException("not_found", "Integration resolution not found");
        }
        ResolveData data = envelope.data();
        return new ResolvedGitIntegration(
                data.connection().tenantId(),
                data.connection().orgId(),
                data.connection().id(),
                data.binding().scopeId(),
                data.connection().providerKey(),
                data.connection().status(),
                data.binding().status(),
                externalRepositoryId(data),
                data.credentialKind(),
                Set.copyOf(data.binding().capabilities() == null ? List.of() : data.binding().capabilities()),
                data.connection().config(),
                data.binding().config());
    }

    @Override
    public CredentialLease leaseCredential(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind,
            String serviceName) {
        String body = jsonb.toJson(Map.of(
                "tenant_id", tenantId.toString(),
                "org_id", orgId.toString(),
                "credential_kind", credentialKind));
        URI uri = baseUri.resolve("/internal/v1/integrations/connections/" + connectionId + "/credential-leases");
        HttpResult result = send(HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json"));
        CredentialLeaseEnvelope envelope = jsonb.fromJson(result.body(), CredentialLeaseEnvelope.class);
        if (envelope == null || envelope.data() == null) {
            throw new GitIntegrationException("not_found", "Integration credential lease not found");
        }
        CredentialLeaseData data = envelope.data();
        return new CredentialLease(
                UUID.nameUUIDFromBytes(data.secretRef().getBytes(StandardCharsets.UTF_8)),
                credentialKind,
                data.secret().toCharArray(),
                data.expiresAt());
    }

    private HttpResult send(HttpRequest.Builder builder) {
        HttpRequest request = builder
                .header("X-Vericov-Service-Name", serviceName)
                .header("X-Vericov-Service-Token", serviceToken)
                .build();
        try {
            HttpResult result = transport.send(request);
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                throw new GitIntegrationException("not_found", "Integrations Config request failed");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Integrations Config request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Integrations Config request interrupted", exception);
        }
    }

    private static String externalRepositoryId(ResolveData data) {
        Object bindingExternal = data.binding().config() == null ? null : data.binding().config().get("external_repository_id");
        if (bindingExternal instanceof String value && !value.isBlank()) {
            return value;
        }
        Object connectionExternal = data.connection().config() == null
                ? null
                : data.connection().config().get("external_repository_id");
        if (connectionExternal instanceof String value && !value.isBlank()) {
            return value;
        }
        return data.connection().externalAccountId();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public interface HttpTransport {
        HttpResult send(HttpRequest request) throws IOException, InterruptedException;
    }

    public record HttpResult(int statusCode, String body) {
    }

    private record JavaHttpTransport(HttpClient httpClient) implements HttpTransport {
        @Override
        public HttpResult send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        }
    }

    public record ResolveEnvelope(ResolveData data) {
    }

    public record ResolveData(
            ConnectionData connection,
            BindingData binding,
            @JsonbProperty("credential_kind")
            String credentialKind) {
    }

    public record ConnectionData(
            UUID id,
            @JsonbProperty("tenant_id")
            UUID tenantId,
            @JsonbProperty("org_id")
            UUID orgId,
            @JsonbProperty("provider_key")
            String providerKey,
            @JsonbProperty("external_account_id")
            String externalAccountId,
            String status,
            Map<String, Object> config) {
    }

    public record BindingData(
            UUID id,
            @JsonbProperty("scope_id")
            UUID scopeId,
            List<String> capabilities,
            Map<String, Object> config,
            String status) {
    }

    public record CredentialLeaseEnvelope(CredentialLeaseData data) {
    }

    public record CredentialLeaseData(
            @JsonbProperty("secret_ref")
            String secretRef,
            String secret,
            @JsonbProperty("expires_at")
            Instant expiresAt) {
    }
}
