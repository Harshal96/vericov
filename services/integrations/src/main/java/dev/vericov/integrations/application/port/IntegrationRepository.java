package dev.vericov.integrations.application.port;

import dev.vericov.integrations.application.IntegrationBindingDetails;
import dev.vericov.integrations.application.IntegrationConnectionDetails;
import dev.vericov.integrations.application.IntegrationCredentialDetails;
import dev.vericov.integrations.application.IntegrationEventDetails;
import dev.vericov.integrations.application.IntegrationSyncStateDetails;
import dev.vericov.integrations.application.IntegrationWebhookEndpointDetails;
import dev.vericov.integrations.application.ResolvedIntegration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationRepository {
    List<IntegrationConnectionDetails> listConnections(UUID tenantId, UUID orgId);

    Optional<IntegrationConnectionDetails> findConnection(UUID tenantId, UUID orgId, UUID connectionId);

    Optional<IntegrationConnectionDetails> findActiveConnection(
            UUID tenantId,
            UUID orgId,
            String providerKey,
            String externalAccountId);

    IntegrationConnectionDetails saveConnection(IntegrationConnectionDetails connection);

    IntegrationConnectionDetails updateConnection(IntegrationConnectionDetails connection, Instant expectedUpdatedAt);

    List<IntegrationBindingDetails> listBindings(UUID tenantId, UUID orgId, UUID connectionId);

    Optional<IntegrationBindingDetails> findBinding(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String scopeType,
            UUID scopeId);

    IntegrationBindingDetails upsertBinding(IntegrationBindingDetails binding, Instant expectedUpdatedAt);

    IntegrationCredentialDetails saveCredential(IntegrationCredentialDetails credential);

    List<IntegrationCredentialDetails> listCredentials(UUID tenantId, UUID orgId, UUID connectionId);

    Optional<IntegrationCredentialDetails> findActiveCredential(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind);

    IntegrationWebhookEndpointDetails saveWebhookEndpoint(IntegrationWebhookEndpointDetails endpoint);

    List<IntegrationWebhookEndpointDetails> listWebhookEndpoints(UUID tenantId, UUID orgId, UUID connectionId);

    Optional<IntegrationWebhookEndpointDetails> findWebhookEndpoint(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            UUID endpointId);

    Optional<ResolvedIntegration> resolve(
            UUID tenantId,
            UUID orgId,
            String providerKey,
            String scopeType,
            UUID scopeId,
            String capability,
            String credentialKind);

    IntegrationSyncStateDetails upsertSyncState(IntegrationSyncStateDetails syncState);

    IntegrationEventDetails recordEvent(IntegrationEventDetails event);
}
