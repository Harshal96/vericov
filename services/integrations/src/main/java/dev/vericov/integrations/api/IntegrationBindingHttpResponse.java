package dev.vericov.integrations.api;

import dev.vericov.integrations.application.IntegrationBindingDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record IntegrationBindingHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("connection_id")
        UUID connectionId,
        @JsonbProperty("scope_type")
        String scopeType,
        @JsonbProperty("scope_id")
        UUID scopeId,
        List<String> capabilities,
        Map<String, Object> config,
        String status,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static IntegrationBindingHttpResponse from(IntegrationBindingDetails binding) {
        return new IntegrationBindingHttpResponse(
                binding.id(),
                binding.tenantId(),
                binding.connectionId(),
                binding.scopeType(),
                binding.scopeId(),
                binding.capabilities(),
                binding.config(),
                binding.status(),
                binding.createdAt(),
                binding.updatedAt());
    }
}
