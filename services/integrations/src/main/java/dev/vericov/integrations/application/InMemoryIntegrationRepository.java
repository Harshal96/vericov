package dev.vericov.integrations.application;

import dev.vericov.integrations.application.port.IntegrationRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIntegrationRepository implements IntegrationRepository {
    private final Map<UUID, IntegrationConnectionDetails> connectionsById = new ConcurrentHashMap<>();
    private final Map<UUID, IntegrationBindingDetails> bindingsById = new ConcurrentHashMap<>();
    private final Map<UUID, IntegrationCredentialDetails> credentialsById = new ConcurrentHashMap<>();
    private final Map<UUID, IntegrationWebhookEndpointDetails> webhookEndpointsById = new ConcurrentHashMap<>();
    private final Map<SyncStateKey, IntegrationSyncStateDetails> syncStatesByKey = new ConcurrentHashMap<>();
    private final Map<UUID, IntegrationEventDetails> eventsById = new ConcurrentHashMap<>();

    @Override
    public List<IntegrationConnectionDetails> listConnections(UUID tenantId, UUID orgId) {
        return connectionsById.values().stream()
                .filter(connection -> connection.tenantId().equals(tenantId))
                .filter(connection -> connection.orgId().equals(orgId))
                .sorted(Comparator
                        .comparing(IntegrationConnectionDetails::displayName)
                        .thenComparing(IntegrationConnectionDetails::createdAt)
                        .thenComparing(IntegrationConnectionDetails::id))
                .toList();
    }

    @Override
    public Optional<IntegrationConnectionDetails> findConnection(UUID tenantId, UUID orgId, UUID connectionId) {
        return Optional.ofNullable(connectionsById.get(connectionId))
                .filter(connection -> connection.tenantId().equals(tenantId))
                .filter(connection -> connection.orgId().equals(orgId));
    }

    @Override
    public Optional<IntegrationConnectionDetails> findActiveConnection(
            UUID tenantId,
            UUID orgId,
            String providerKey,
            String externalAccountId) {
        return connectionsById.values().stream()
                .filter(connection -> connection.tenantId().equals(tenantId))
                .filter(connection -> connection.orgId().equals(orgId))
                .filter(connection -> connection.providerKey().equals(providerKey))
                .filter(connection -> connection.externalAccountId().equals(externalAccountId))
                .filter(connection -> "active".equals(connection.status()))
                .findFirst();
    }

    @Override
    public synchronized IntegrationConnectionDetails saveConnection(IntegrationConnectionDetails connection) {
        if (connectionsById.containsKey(connection.id())) {
            throw new IntegrationException("conflict", "Integration connection already exists");
        }
        if ("active".equals(connection.status())) {
            findActiveConnection(
                    connection.tenantId(),
                    connection.orgId(),
                    connection.providerKey(),
                    connection.externalAccountId()).ifPresent(existing -> {
                throw new IntegrationException("conflict", "Integration connection already exists");
            });
        }
        connectionsById.put(connection.id(), connection);
        return connection;
    }

    @Override
    public List<IntegrationBindingDetails> listBindings(UUID tenantId, UUID orgId, UUID connectionId) {
        if (findConnection(tenantId, orgId, connectionId).isEmpty()) {
            return List.of();
        }
        return bindingsById.values().stream()
                .filter(binding -> binding.tenantId().equals(tenantId))
                .filter(binding -> binding.connectionId().equals(connectionId))
                .sorted(Comparator
                        .comparing(IntegrationBindingDetails::scopeType)
                        .thenComparing(binding -> binding.scopeId().toString())
                        .thenComparing(IntegrationBindingDetails::createdAt)
                        .thenComparing(IntegrationBindingDetails::id))
                .toList();
    }

    @Override
    public Optional<IntegrationBindingDetails> findBinding(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String scopeType,
            UUID scopeId) {
        if (findConnection(tenantId, orgId, connectionId).isEmpty()) {
            return Optional.empty();
        }
        return bindingsById.values().stream()
                .filter(binding -> binding.tenantId().equals(tenantId))
                .filter(binding -> binding.connectionId().equals(connectionId))
                .filter(binding -> binding.scopeType().equals(scopeType))
                .filter(binding -> binding.scopeId().equals(scopeId))
                .findFirst();
    }

    @Override
    public synchronized IntegrationBindingDetails upsertBinding(
            IntegrationBindingDetails binding,
            Instant expectedUpdatedAt) {
        IntegrationConnectionDetails connection = connectionsById.get(binding.connectionId());
        if (connection == null || !connection.tenantId().equals(binding.tenantId())) {
            throw new IntegrationException("not_found", "Integration connection not found");
        }
        Optional<IntegrationBindingDetails> existingComposite = bindingsById.values().stream()
                .filter(existing -> existing.tenantId().equals(binding.tenantId())
                        && existing.connectionId().equals(binding.connectionId())
                        && existing.scopeType().equals(binding.scopeType())
                        && existing.scopeId().equals(binding.scopeId()))
                .findFirst();
        if (existingComposite.isEmpty()) {
            if (expectedUpdatedAt != null) {
                throw new IntegrationException("not_found", "Integration binding not found");
            }
            if (bindingsById.containsKey(binding.id())) {
                throw new IntegrationException("conflict", "Integration binding already exists");
            }
            bindingsById.put(binding.id(), binding);
            return binding;
        }
        IntegrationBindingDetails current = existingComposite.orElseThrow();
        if (expectedUpdatedAt == null) {
            throw new IntegrationException("conflict", "Integration binding already exists");
        }
        if (!current.updatedAt().equals(expectedUpdatedAt)) {
            throw new IntegrationException("conflict", "Integration binding was modified");
        }
        Instant updatedAt = binding.updatedAt().isAfter(current.updatedAt())
                ? binding.updatedAt()
                : current.updatedAt().plusNanos(1);
        IntegrationBindingDetails updated = new IntegrationBindingDetails(
                current.id(),
                current.tenantId(),
                current.connectionId(),
                current.scopeType(),
                current.scopeId(),
                binding.capabilities(),
                binding.config(),
                binding.status(),
                current.createdAt(),
                updatedAt);
        bindingsById.put(current.id(), updated);
        return updated;
    }

    @Override
    public synchronized IntegrationCredentialDetails saveCredential(IntegrationCredentialDetails credential) {
        IntegrationConnectionDetails connection = connectionsById.get(credential.connectionId());
        if (connection == null || !connection.tenantId().equals(credential.tenantId())) {
            throw new IntegrationException("not_found", "Integration connection not found");
        }
        if (credentialsById.containsKey(credential.id())) {
            throw new IntegrationException("conflict", "Integration credential already exists");
        }
        findActiveCredential(
                connection.tenantId(),
                connection.orgId(),
                connection.id(),
                credential.credentialKind()).ifPresent(existing -> {
            throw new IntegrationException("conflict", "Integration credential already exists");
        });
        credentialsById.put(credential.id(), credential);
        return credential;
    }

    @Override
    public List<IntegrationCredentialDetails> listCredentials(UUID tenantId, UUID orgId, UUID connectionId) {
        if (findConnection(tenantId, orgId, connectionId).isEmpty()) {
            return List.of();
        }
        return credentialsById.values().stream()
                .filter(credential -> credential.tenantId().equals(tenantId))
                .filter(credential -> credential.connectionId().equals(connectionId))
                .sorted(Comparator
                        .comparing(IntegrationCredentialDetails::credentialKind)
                        .thenComparing(IntegrationCredentialDetails::createdAt)
                        .thenComparing(IntegrationCredentialDetails::id))
                .toList();
    }

    @Override
    public Optional<IntegrationCredentialDetails> findActiveCredential(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind) {
        if (findConnection(tenantId, orgId, connectionId).isEmpty()) {
            return Optional.empty();
        }
        return credentialsById.values().stream()
                .filter(credential -> credential.tenantId().equals(tenantId))
                .filter(credential -> credential.connectionId().equals(connectionId))
                .filter(credential -> credential.credentialKind().equals(credentialKind))
                .filter(credential -> "active".equals(credential.status()))
                .findFirst();
    }

    @Override
    public synchronized IntegrationWebhookEndpointDetails saveWebhookEndpoint(IntegrationWebhookEndpointDetails endpoint) {
        IntegrationConnectionDetails connection = connectionsById.get(endpoint.connectionId());
        if (connection == null
                || !connection.tenantId().equals(endpoint.tenantId())
                || !connection.orgId().equals(endpoint.orgId())
                || !connection.providerKey().equals(endpoint.providerKey())) {
            throw new IntegrationException("not_found", "Integration connection not found");
        }
        if (webhookEndpointsById.containsKey(endpoint.id())) {
            throw new IntegrationException("conflict", "Integration webhook endpoint already exists");
        }
        webhookEndpointsById.put(endpoint.id(), endpoint);
        return endpoint;
    }

    @Override
    public List<IntegrationWebhookEndpointDetails> listWebhookEndpoints(UUID tenantId, UUID orgId, UUID connectionId) {
        if (findConnection(tenantId, orgId, connectionId).isEmpty()) {
            return List.of();
        }
        return webhookEndpointsById.values().stream()
                .filter(endpoint -> endpoint.tenantId().equals(tenantId))
                .filter(endpoint -> endpoint.orgId().equals(orgId))
                .filter(endpoint -> endpoint.connectionId().equals(connectionId))
                .sorted(Comparator
                        .comparing(IntegrationWebhookEndpointDetails::providerKey)
                        .thenComparing(IntegrationWebhookEndpointDetails::createdAt)
                        .thenComparing(IntegrationWebhookEndpointDetails::id))
                .toList();
    }

    @Override
    public Optional<IntegrationWebhookEndpointDetails> findWebhookEndpoint(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            UUID endpointId) {
        return Optional.ofNullable(webhookEndpointsById.get(endpointId))
                .filter(endpoint -> endpoint.tenantId().equals(tenantId))
                .filter(endpoint -> endpoint.orgId().equals(orgId))
                .filter(endpoint -> endpoint.connectionId().equals(connectionId));
    }

    @Override
    public Optional<ResolvedIntegration> resolve(
            UUID tenantId,
            UUID orgId,
            String providerKey,
            String scopeType,
            UUID scopeId,
            String capability,
            String credentialKind) {
        List<ResolvedIntegration> matches = connectionsById.values().stream()
                .filter(connection -> connection.tenantId().equals(tenantId))
                .filter(connection -> connection.orgId().equals(orgId))
                .filter(connection -> connection.providerKey().equals(providerKey))
                .filter(connection -> "active".equals(connection.status()))
                .filter(connection -> findActiveCredential(
                        tenantId,
                        orgId,
                        connection.id(),
                        credentialKind).isPresent())
                .flatMap(connection -> bindingsById.values().stream()
                        .filter(binding -> binding.tenantId().equals(tenantId))
                        .filter(binding -> binding.connectionId().equals(connection.id()))
                        .filter(binding -> binding.scopeType().equals(scopeType))
                        .filter(binding -> binding.scopeId().equals(scopeId))
                        .filter(binding -> "active".equals(binding.status()))
                        .filter(binding -> binding.capabilities().contains(capability))
                        .map(binding -> new ResolvedIntegration(connection, binding, credentialKind)))
                .sorted(Comparator
                        .comparing((ResolvedIntegration resolved) -> resolved.binding().createdAt())
                        .thenComparing(resolved -> resolved.binding().id()))
                .toList();
        if (matches.size() > 1) {
            throw new IntegrationException("conflict", "Integration resolution is ambiguous");
        }
        return matches.stream().findFirst();
    }

    @Override
    public synchronized IntegrationSyncStateDetails upsertSyncState(IntegrationSyncStateDetails syncState) {
        IntegrationConnectionDetails connection = connectionsById.get(syncState.connectionId());
        if (connection == null
                || !connection.tenantId().equals(syncState.tenantId())
                || !connection.orgId().equals(syncState.orgId())) {
            throw new IntegrationException("not_found", "Integration connection not found");
        }
        SyncStateKey key = new SyncStateKey(
                syncState.tenantId(),
                syncState.connectionId(),
                syncState.scopeType(),
                syncState.scopeId(),
                syncState.syncType());
        IntegrationSyncStateDetails current = syncStatesByKey.get(key);
        if (current == null) {
            syncStatesByKey.put(key, syncState);
            return syncState;
        }
        Instant updatedAt = syncState.updatedAt().isAfter(current.updatedAt())
                ? syncState.updatedAt()
                : current.updatedAt().plusNanos(1);
        IntegrationSyncStateDetails updated = new IntegrationSyncStateDetails(
                current.id(),
                current.tenantId(),
                current.orgId(),
                current.connectionId(),
                current.syncType(),
                current.scopeType(),
                current.scopeId(),
                syncState.status(),
                syncState.cursor(),
                syncState.checkpoint(),
                syncState.lastError(),
                syncState.lastStartedAt(),
                syncState.lastCompletedAt(),
                syncState.nextRunAt(),
                syncState.leaseExpiresAt(),
                current.createdAt(),
                updatedAt);
        syncStatesByKey.put(key, updated);
        return updated;
    }

    @Override
    public synchronized IntegrationEventDetails recordEvent(IntegrationEventDetails event) {
        if (eventsById.containsKey(event.id())) {
            throw new IntegrationException("conflict", "Integration event already exists");
        }
        if (event.connectionId() != null) {
            IntegrationConnectionDetails connection = connectionsById.get(event.connectionId());
            if (connection == null
                    || !connection.tenantId().equals(event.tenantId())
                    || !connection.orgId().equals(event.orgId())
                    || !connection.providerKey().equals(event.providerKey())) {
                throw new IntegrationException("not_found", "Integration connection not found");
            }
        }
        eventsById.put(event.id(), event);
        return event;
    }

    @Override
    public synchronized IntegrationConnectionDetails updateConnection(
            IntegrationConnectionDetails connection,
            Instant expectedUpdatedAt) {
        IntegrationConnectionDetails current = connectionsById.get(connection.id());
        if (current == null
                || !current.tenantId().equals(connection.tenantId())
                || !current.orgId().equals(connection.orgId())) {
            throw new IntegrationException("not_found", "Integration connection not found");
        }
        if (!current.updatedAt().equals(expectedUpdatedAt)) {
            throw new IntegrationException("conflict", "Integration connection was modified");
        }
        if ("active".equals(connection.status())) {
            boolean duplicate = connectionsById.values().stream()
                    .anyMatch(existing -> !existing.id().equals(connection.id())
                            && existing.tenantId().equals(connection.tenantId())
                            && existing.orgId().equals(connection.orgId())
                            && existing.providerKey().equals(connection.providerKey())
                            && existing.externalAccountId().equals(connection.externalAccountId())
                            && "active".equals(existing.status()));
            if (duplicate) {
                throw new IntegrationException("conflict", "Integration connection already exists");
            }
        }
        connectionsById.put(connection.id(), connection);
        return connection;
    }

    private record SyncStateKey(
            UUID tenantId,
            UUID connectionId,
            String scopeType,
            UUID scopeId,
            String syncType) {
    }
}
