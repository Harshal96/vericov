package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.EffectiveRepositoryConfig;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EffectiveRepositoryConfigHttpResponse(
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("org_defaults")
        Map<String, Object> orgDefaults,
        @JsonbProperty("repository_config")
        Map<String, Object> repositoryConfig,
        List<RepositoryPolicyHttpResponse> policies,
        List<RepositoryGateHttpResponse> gates,
        @JsonbProperty("resolved_at")
        Instant resolvedAt) {

    public static EffectiveRepositoryConfigHttpResponse from(EffectiveRepositoryConfig config) {
        return new EffectiveRepositoryConfigHttpResponse(
                config.tenantId(),
                config.organizationId(),
                config.repositoryId(),
                config.orgDefaults(),
                config.repositoryConfig(),
                config.policies().stream().map(RepositoryPolicyHttpResponse::from).toList(),
                config.gates().stream().map(RepositoryGateHttpResponse::from).toList(),
                config.resolvedAt());
    }
}
