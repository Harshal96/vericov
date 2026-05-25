package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EffectiveRepositoryConfig(
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        Map<String, Object> orgDefaults,
        Map<String, Object> repositoryConfig,
        List<RepositoryPolicyDetails> policies,
        List<RepositoryGateDetails> gates,
        Instant resolvedAt) {

    public EffectiveRepositoryConfig {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        orgDefaults = ConfigurationValues.deepCopyMap(orgDefaults);
        repositoryConfig = ConfigurationValues.deepCopyMap(repositoryConfig);
        policies = List.copyOf(policies == null ? List.of() : policies);
        gates = List.copyOf(gates == null ? List.of() : gates);
        Objects.requireNonNull(resolvedAt, "resolvedAt");
    }
}
