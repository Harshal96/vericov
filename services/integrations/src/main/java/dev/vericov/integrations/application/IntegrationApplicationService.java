package dev.vericov.integrations.application;

import dev.vericov.integrations.application.port.CredentialVault;
import dev.vericov.integrations.application.port.IntegrationRepository;
import dev.vericov.integrations.application.port.IntegrationScopeValidator;
import dev.vericov.integrations.application.port.ProviderRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class IntegrationApplicationService {
    private static final int MAX_DISPLAY_NAME_LENGTH = 120;
    private static final int MAX_SYNC_TYPE_LENGTH = 120;
    private static final int MAX_EVENT_TYPE_LENGTH = 160;
    private static final int MAX_EXTERNAL_EVENT_ID_LENGTH = 255;
    private static final int MAX_ENDPOINT_URL_LENGTH = 2000;
    private static final int MAX_SECRET_REF_LENGTH = 500;
    private static final Set<String> STATUSES = Set.of("draft", "active", "needs_reauth", "disabled", "revoked");
    private static final Set<String> BINDING_SCOPE_TYPES = Set.of("organization", "repository", "component");
    private static final Set<String> BINDING_STATUSES = Set.of("active", "disabled");
    private static final Set<String> SYNC_STATUSES = Set.of("idle", "running", "succeeded", "failed", "paused");
    private static final Set<String> EVENT_STATUSES = Set.of("pending", "processed", "failed", "ignored");
    private static final Set<String> CREDENTIAL_KINDS = Set.of(
            "oauth_access_token",
            "oauth_refresh_token",
            "github_app_private_key",
            "webhook_secret",
            "api_token");

    private final IntegrationRepository repository;
    private final ProviderRegistry providerRegistry;
    private final CredentialVault credentialVault;
    private final IntegrationScopeValidator scopeValidator;
    private final Clock clock;

    public IntegrationApplicationService(
            IntegrationRepository repository,
            ProviderRegistry providerRegistry,
            CredentialVault credentialVault,
            IntegrationScopeValidator scopeValidator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.credentialVault = Objects.requireNonNull(credentialVault, "credentialVault");
        this.scopeValidator = Objects.requireNonNull(scopeValidator, "scopeValidator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<IntegrationConnectionDetails> listConnections(UUID requesterUserId, UUID tenantId, UUID orgId) {
        requireId(requesterUserId, "requester_user_id is required");
        requireId(tenantId, "tenant_id is required");
        requireId(orgId, "org_id is required");
        return repository.listConnections(tenantId, orgId);
    }

    public IntegrationConnectionDetails createConnection(CreateIntegrationConnectionCommand command) {
        Objects.requireNonNull(command, "command");
        requireId(command.requesterUserId(), "requester_user_id is required");
        requireId(command.tenantId(), "tenant_id is required");
        requireId(command.orgId(), "org_id is required");

        ProviderDefinition provider = requireProvider(command.providerKey());
        String displayName = validateDisplayName(command.displayName());
        String externalAccountId = validateExternalAccountId(command.externalAccountId());
        String externalAccountName = trimOptional(command.externalAccountName());
        Map<String, Object> config = validateConfig(command.config());

        repository.findActiveConnection(
                command.tenantId(),
                command.orgId(),
                provider.providerKey(),
                externalAccountId).ifPresent(existing -> {
                    throw new IntegrationException("conflict", "Integration connection already exists");
                });

        Instant now = clock.instant();
        return repository.saveConnection(new IntegrationConnectionDetails(
                UUID.randomUUID(),
                command.tenantId(),
                command.orgId(),
                provider.providerKey(),
                provider.type(),
                displayName,
                externalAccountId,
                externalAccountName,
                "active",
                config,
                command.requesterUserId(),
                null,
                now,
                now));
    }

    public IntegrationConnectionDetails getConnection(UUID requesterUserId, UUID tenantId, UUID orgId, UUID connectionId) {
        requireId(requesterUserId, "requester_user_id is required");
        requireId(tenantId, "tenant_id is required");
        requireId(orgId, "org_id is required");
        requireId(connectionId, "connection_id is required");
        return repository.findConnection(tenantId, orgId, connectionId)
                .orElseThrow(() -> new IntegrationException("not_found", "Integration connection not found"));
    }

    public IntegrationConnectionDetails updateConnection(UpdateIntegrationConnectionCommand command) {
        Objects.requireNonNull(command, "command");
        requireId(command.requesterUserId(), "requester_user_id is required");
        requireId(command.tenantId(), "tenant_id is required");
        requireId(command.orgId(), "org_id is required");
        requireId(command.connectionId(), "connection_id is required");
        requireInstant(command.expectedUpdatedAt(), "expected_updated_at is required");
        IntegrationConnectionDetails current = repository.findConnection(command.tenantId(), command.orgId(), command.connectionId())
                .orElseThrow(() -> new IntegrationException("not_found", "Integration connection not found"));

        String nextDisplayName = command.displayName() == null
                ? current.displayName()
                : validateDisplayName(command.displayName());
        String nextStatus = command.status() == null ? current.status() : validateStatus(command.status());
        Map<String, Object> nextConfig = command.config() == null ? current.config() : validateConfig(command.config());

        if ("active".equals(nextStatus)) {
            repository.findActiveConnection(
                            current.tenantId(),
                            current.orgId(),
                            current.providerKey(),
                            current.externalAccountId())
                    .filter(existing -> !existing.id().equals(current.id()))
                    .ifPresent(existing -> {
                        throw new IntegrationException("conflict", "Integration connection already exists");
                    });
        }

        return repository.updateConnection(current.withValues(
                nextDisplayName,
                nextStatus,
                nextConfig,
                nextUpdatedAt(current.updatedAt())), command.expectedUpdatedAt());
    }

    public IntegrationConnectionDetails disableConnection(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            Instant expectedUpdatedAt) {
        requireId(requesterUserId, "requester_user_id is required");
        requireId(tenantId, "tenant_id is required");
        requireId(orgId, "org_id is required");
        requireId(connectionId, "connection_id is required");
        requireInstant(expectedUpdatedAt, "expected_updated_at is required");
        IntegrationConnectionDetails current = repository.findConnection(tenantId, orgId, connectionId)
                .orElseThrow(() -> new IntegrationException("not_found", "Integration connection not found"));
        return repository.updateConnection(current.withStatus("disabled", nextUpdatedAt(current.updatedAt())), expectedUpdatedAt);
    }

    public List<IntegrationBindingDetails> listBindings(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId,
            UUID connectionId) {
        requireId(requesterUserId, "requester_user_id is required");
        IntegrationConnectionDetails connection = requireConnection(tenantId, orgId, connectionId);
        return repository.listBindings(connection.tenantId(), connection.orgId(), connection.id());
    }

    public IntegrationBindingDetails upsertBinding(UpsertIntegrationBindingCommand command) {
        Objects.requireNonNull(command, "command");
        requireId(command.requesterUserId(), "requester_user_id is required");
        IntegrationConnectionDetails connection = requireConnection(
                command.tenantId(),
                command.orgId(),
                command.connectionId());
        ProviderDefinition provider = requireProvider(connection.providerKey());
        String scopeType = validateBindingScopeType(command.scopeType());
        requireId(command.scopeId(), "scope_id is required");
        List<String> capabilities = validateBindingCapabilities(command.capabilities(), provider);
        Map<String, Object> config = validateConfig(command.config());
        String status = validateBindingStatus(command.status());
        scopeValidator.requireScope(connection.tenantId(), connection.orgId(), scopeType, command.scopeId());
        Instant now = clock.instant();

        return repository.upsertBinding(new IntegrationBindingDetails(
                UUID.randomUUID(),
                connection.tenantId(),
                connection.id(),
                scopeType,
                command.scopeId(),
                capabilities,
                config,
                status,
                now,
                now), command.expectedUpdatedAt());
    }

    public IntegrationBindingDetails disableBinding(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String scopeType,
            UUID scopeId,
            Instant expectedUpdatedAt) {
        requireId(requesterUserId, "requester_user_id is required");
        requireInstant(expectedUpdatedAt, "expected_updated_at is required");
        IntegrationConnectionDetails connection = requireConnection(tenantId, orgId, connectionId);
        String normalizedScopeType = validateBindingScopeType(scopeType);
        requireId(scopeId, "scope_id is required");
        IntegrationBindingDetails current = repository.findBinding(
                        connection.tenantId(),
                        connection.orgId(),
                        connection.id(),
                        normalizedScopeType,
                        scopeId)
                .orElseThrow(() -> new IntegrationException("not_found", "Integration binding not found"));
        return repository.upsertBinding(
                current.withStatus("disabled", clock.instant()),
                expectedUpdatedAt);
    }

    public ResolvedIntegration resolveIntegration(
            UUID tenantId,
            UUID orgId,
            String providerKey,
            String scopeType,
            UUID scopeId,
            String capability) {
        requireId(tenantId, "tenant_id is required");
        requireId(orgId, "org_id is required");
        String normalizedProviderKey = validateRequiredText(providerKey, "provider_key is required")
                .toLowerCase(Locale.ROOT);
        ProviderDefinition provider = requireProvider(normalizedProviderKey);
        String normalizedScopeType = validateBindingScopeType(scopeType);
        requireId(scopeId, "scope_id is required");
        String normalizedCapability = validateRequiredText(capability, "capability is required")
                .toLowerCase(Locale.ROOT);
        if (!provider.capabilities().contains(normalizedCapability)) {
            throw new IntegrationException("validation_error", "capability is not supported by provider");
        }
        String credentialKind = provider.credentialKindForCapability(normalizedCapability);
        scopeValidator.requireScope(tenantId, orgId, normalizedScopeType, scopeId);
        return repository.resolve(
                        tenantId,
                        orgId,
                        normalizedProviderKey,
                        normalizedScopeType,
                        scopeId,
                        normalizedCapability,
                        credentialKind)
                .orElseThrow(() -> new IntegrationException("not_found", "Integration resolution not found"));
    }

    public IntegrationCredentialDetails createCredential(CreateCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        requireId(command.requesterUserId(), "requester_user_id is required");
        IntegrationConnectionDetails connection = requireConnection(
                command.tenantId(),
                command.orgId(),
                command.connectionId());
        requireActiveConnection(connection);
        String credentialKind = validateCredentialKind(command.credentialKind());
        char[] secret = command.secret();
        validateSecret(secret);

        repository.findActiveCredential(
                connection.tenantId(),
                connection.orgId(),
                connection.id(),
                credentialKind).ifPresent(existing -> {
            throw new IntegrationException("conflict", "Integration credential already exists");
        });

        String secretRef;
        try {
            secretRef = credentialVault.store(connection.tenantId(), connection.id(), credentialKind, secret);
        } finally {
            Arrays.fill(secret, '\0');
        }

        Instant now = clock.instant();
        return repository.saveCredential(new IntegrationCredentialDetails(
                UUID.randomUUID(),
                connection.tenantId(),
                connection.id(),
                credentialKind,
                secretRef,
                1,
                "active",
                command.expiresAt(),
                now,
                now,
                now));
    }

    public List<IntegrationCredentialDetails> listCredentials(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId,
            UUID connectionId) {
        requireId(requesterUserId, "requester_user_id is required");
        IntegrationConnectionDetails connection = requireConnection(tenantId, orgId, connectionId);
        return repository.listCredentials(connection.tenantId(), connection.orgId(), connection.id());
    }

    public IntegrationWebhookEndpointDetails createWebhookEndpoint(CreateIntegrationWebhookEndpointCommand command) {
        Objects.requireNonNull(command, "command");
        requireId(command.requesterUserId(), "requester_user_id is required");
        IntegrationConnectionDetails connection = requireConnection(
                command.tenantId(),
                command.orgId(),
                command.connectionId());
        String providerKey = validateRequiredText(command.providerKey(), "provider_key is required").toLowerCase(Locale.ROOT);
        if (!connection.providerKey().equals(providerKey)) {
            throw new IntegrationException("validation_error", "provider_key does not match connection");
        }
        String endpointUrl = validateBoundedText(
                command.endpointUrl(),
                "endpoint_url is required",
                MAX_ENDPOINT_URL_LENGTH,
                "endpoint_url must be 2000 characters or less");
        List<String> eventTypes = validateEventTypes(command.eventTypes());
        String signingSecretRef = validateBoundedText(
                command.signingSecretRef(),
                "signing_secret_ref is required",
                MAX_SECRET_REF_LENGTH,
                "signing_secret_ref must be 500 characters or less");
        Map<String, Object> config = validateConfig(command.config());
        Instant now = clock.instant();
        return repository.saveWebhookEndpoint(new IntegrationWebhookEndpointDetails(
                UUID.randomUUID(),
                connection.tenantId(),
                connection.orgId(),
                connection.id(),
                providerKey,
                trimOptional(command.externalWebhookId()),
                endpointUrl,
                eventTypes,
                "active",
                signingSecretRef,
                config,
                Map.of(),
                null,
                now,
                now));
    }

    public List<IntegrationWebhookEndpointDetails> listWebhookEndpoints(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId,
            UUID connectionId) {
        requireId(requesterUserId, "requester_user_id is required");
        IntegrationConnectionDetails connection = requireConnection(tenantId, orgId, connectionId);
        return repository.listWebhookEndpoints(connection.tenantId(), connection.orgId(), connection.id());
    }

    public CredentialLease leaseCredential(
            String requestedBy,
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind) {
        String normalizedRequestedBy = validateRequiredText(requestedBy, "requested_by is required");
        IntegrationConnectionDetails connection = requireConnection(tenantId, orgId, connectionId);
        if (!"active".equals(connection.status())) {
            throw new IntegrationException("not_found", "Integration credential not found");
        }
        String normalizedCredentialKind = validateCredentialKind(credentialKind);
        IntegrationCredentialDetails credential = repository.findActiveCredential(
                        connection.tenantId(),
                        connection.orgId(),
                        connection.id(),
                        normalizedCredentialKind)
                .orElseThrow(() -> new IntegrationException("not_found", "Integration credential not found"));
        return credentialVault.lease(
                connection.tenantId(),
                connection.id(),
                credential.secretRef(),
                normalizedRequestedBy);
    }

    public IntegrationSyncStateDetails updateSyncState(UpdateIntegrationSyncStateCommand command) {
        Objects.requireNonNull(command, "command");
        validateRequiredText(command.requestedBy(), "requested_by is required");
        IntegrationConnectionDetails connection = requireConnection(
                command.tenantId(),
                command.orgId(),
                command.connectionId());
        String syncType = validateBoundedText(command.syncType(), "sync_type is required", MAX_SYNC_TYPE_LENGTH,
                "sync_type must be 120 characters or less");
        String scopeType = validateBindingScopeType(command.scopeType());
        requireId(command.scopeId(), "scope_id is required");
        String status = validateSyncStatus(command.status());
        Map<String, Object> cursor = validateConfig(command.cursor());
        Map<String, Object> checkpoint = validateConfig(command.checkpoint());
        Map<String, Object> lastError = validateConfig(command.lastError());
        scopeValidator.requireScope(connection.tenantId(), connection.orgId(), scopeType, command.scopeId());
        Instant now = clock.instant();
        return repository.upsertSyncState(new IntegrationSyncStateDetails(
                UUID.randomUUID(),
                connection.tenantId(),
                connection.orgId(),
                connection.id(),
                syncType,
                scopeType,
                command.scopeId(),
                status,
                cursor,
                checkpoint,
                lastError,
                command.lastStartedAt(),
                command.lastCompletedAt(),
                command.nextRunAt(),
                command.leaseExpiresAt(),
                now,
                now));
    }

    public IntegrationEventDetails recordEvent(RecordIntegrationEventCommand command) {
        Objects.requireNonNull(command, "command");
        validateRequiredText(command.requestedBy(), "requested_by is required");
        IntegrationConnectionDetails connection = requireConnection(
                command.tenantId(),
                command.orgId(),
                command.connectionId());
        String providerKey = command.providerKey() == null
                ? connection.providerKey()
                : validateRequiredText(command.providerKey(), "provider_key is required").toLowerCase(Locale.ROOT);
        if (!connection.providerKey().equals(providerKey)) {
            throw new IntegrationException("validation_error", "provider_key does not match connection");
        }
        String eventType = validateBoundedText(command.eventType(), "event_type is required", MAX_EVENT_TYPE_LENGTH,
                "event_type must be 160 characters or less");
        String externalEventId = validateOptionalBoundedText(
                command.externalEventId(),
                MAX_EXTERNAL_EVENT_ID_LENGTH,
                "external_event_id must be 255 characters or less");
        String scopeType = validateOptionalScopeType(command.scopeType(), command.scopeId());
        String status = validateEventStatus(command.status());
        Map<String, Object> payload = validateConfig(command.payload());
        Map<String, Object> error = validateConfig(command.error());
        if (scopeType != null) {
            scopeValidator.requireScope(connection.tenantId(), connection.orgId(), scopeType, command.scopeId());
        }
        Instant now = clock.instant();
        Instant receivedAt = command.receivedAt() == null ? now : command.receivedAt();
        return repository.recordEvent(new IntegrationEventDetails(
                UUID.randomUUID(),
                connection.tenantId(),
                connection.orgId(),
                connection.id(),
                null,
                providerKey,
                eventType,
                externalEventId,
                scopeType,
                command.scopeId(),
                status,
                payload,
                error,
                receivedAt,
                command.processedAt(),
                now,
                now));
    }

    private Instant nextUpdatedAt(Instant currentUpdatedAt) {
        Instant now = clock.instant();
        return now.isAfter(currentUpdatedAt) ? now : currentUpdatedAt.plusNanos(1_000);
    }

    private IntegrationConnectionDetails requireConnection(UUID tenantId, UUID orgId, UUID connectionId) {
        requireId(tenantId, "tenant_id is required");
        requireId(orgId, "org_id is required");
        requireId(connectionId, "connection_id is required");
        return repository.findConnection(tenantId, orgId, connectionId)
                .orElseThrow(() -> new IntegrationException("not_found", "Integration connection not found"));
    }

    private static void requireActiveConnection(IntegrationConnectionDetails connection) {
        if (!"active".equals(connection.status())) {
            throw new IntegrationException("validation_error", "connection must be active");
        }
    }

    private ProviderDefinition requireProvider(String providerKey) {
        String normalizedProviderKey = validateRequiredText(providerKey, "provider_key is required").toLowerCase(Locale.ROOT);
        return providerRegistry.findProvider(normalizedProviderKey)
                .orElseThrow(() -> new IntegrationException("validation_error", "provider_key is invalid"));
    }

    private static String validateDisplayName(String displayName) {
        String trimmed = validateRequiredText(displayName, "display_name is required");
        if (trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IntegrationException("validation_error", "display_name must be 120 characters or less");
        }
        return trimmed;
    }

    private static String validateExternalAccountId(String externalAccountId) {
        return validateRequiredText(externalAccountId, "external_account_id is required");
    }

    private static String validateStatus(String status) {
        String normalized = validateRequiredText(status, "status is required").toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IntegrationException("validation_error", "status is invalid");
        }
        return normalized;
    }

    private static String validateBindingScopeType(String scopeType) {
        String normalized = validateRequiredText(scopeType, "scope_type is required").toLowerCase(Locale.ROOT);
        if (!BINDING_SCOPE_TYPES.contains(normalized)) {
            throw new IntegrationException("validation_error", "scope_type is invalid");
        }
        return normalized;
    }

    private static String validateBindingStatus(String status) {
        String normalized = validateRequiredText(status, "status is required").toLowerCase(Locale.ROOT);
        if (!BINDING_STATUSES.contains(normalized)) {
            throw new IntegrationException("validation_error", "status is invalid");
        }
        return normalized;
    }

    private static String validateCredentialKind(String credentialKind) {
        String normalized = validateRequiredText(credentialKind, "credential_kind is required").toLowerCase(Locale.ROOT);
        if (!CREDENTIAL_KINDS.contains(normalized)) {
            throw new IntegrationException("validation_error", "credential_kind is invalid");
        }
        return normalized;
    }

    private static String validateSyncStatus(String status) {
        String normalized = validateRequiredText(status, "status is required").toLowerCase(Locale.ROOT);
        if (!SYNC_STATUSES.contains(normalized)) {
            throw new IntegrationException("validation_error", "status is invalid");
        }
        return normalized;
    }

    private static String validateEventStatus(String status) {
        String normalized = validateRequiredText(status, "status is required").toLowerCase(Locale.ROOT);
        if (!EVENT_STATUSES.contains(normalized)) {
            throw new IntegrationException("validation_error", "status is invalid");
        }
        return normalized;
    }

    private static String validateOptionalScopeType(String scopeType, UUID scopeId) {
        if (scopeType == null || scopeType.isBlank()) {
            if (scopeId != null) {
                throw new IntegrationException("validation_error", "scope_type is required");
            }
            return null;
        }
        if (scopeId == null) {
            throw new IntegrationException("validation_error", "scope_id is required");
        }
        return validateBindingScopeType(scopeType);
    }

    private static void validateSecret(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IntegrationException("validation_error", "secret is required");
        }
    }

    private static List<String> validateBindingCapabilities(
            List<String> capabilities,
            ProviderDefinition provider) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IntegrationException("validation_error", "capabilities are required");
        }
        List<String> normalizedCapabilities = capabilities.stream()
                .map(capability -> validateRequiredText(capability, "capability is required").toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalizedCapabilities.isEmpty()) {
            throw new IntegrationException("validation_error", "capabilities are required");
        }
        for (String capability : normalizedCapabilities) {
            if (!provider.capabilities().contains(capability)) {
                throw new IntegrationException("validation_error", "capability is not supported by provider");
            }
        }
        return normalizedCapabilities;
    }

    private static List<String> validateEventTypes(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IntegrationException("validation_error", "event_types are required");
        }
        List<String> normalized = eventTypes.stream()
                .map(eventType -> validateBoundedText(
                        eventType,
                        "event_type is required",
                        MAX_EVENT_TYPE_LENGTH,
                        "event_type must be 160 characters or less"))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IntegrationException("validation_error", "event_types are required");
        }
        return normalized;
    }

    private static Map<String, Object> validateConfig(Map<String, Object> config) {
        try {
            return IntegrationConfigValues.deepCopyMap(config);
        } catch (IllegalArgumentException exception) {
            throw new IntegrationException("validation_error", exception.getMessage());
        }
    }

    private static void requireId(UUID id, String message) {
        if (id == null) {
            throw new IntegrationException("validation_error", message);
        }
    }

    private static void requireInstant(Instant instant, String message) {
        if (instant == null) {
            throw new IntegrationException("validation_error", message);
        }
    }

    private static String validateRequiredText(String value, String message) {
        String trimmed = trimOptional(value);
        if (trimmed == null) {
            throw new IntegrationException("validation_error", message);
        }
        return trimmed;
    }

    private static String validateBoundedText(String value, String requiredMessage, int maxLength, String lengthMessage) {
        String trimmed = validateRequiredText(value, requiredMessage);
        if (trimmed.length() > maxLength) {
            throw new IntegrationException("validation_error", lengthMessage);
        }
        return trimmed;
    }

    private static String validateOptionalBoundedText(String value, int maxLength, String lengthMessage) {
        String trimmed = trimOptional(value);
        if (trimmed != null && trimmed.length() > maxLength) {
            throw new IntegrationException("validation_error", lengthMessage);
        }
        return trimmed;
    }

    private static String trimOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
