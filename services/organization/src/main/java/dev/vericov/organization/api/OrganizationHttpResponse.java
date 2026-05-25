package dev.vericov.organization.api;

import dev.vericov.organization.application.OrganizationDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record OrganizationHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        String name,
        String slug,
        String plan,
        String status,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static OrganizationHttpResponse from(OrganizationDetails organization) {
        return new OrganizationHttpResponse(
                organization.id(),
                organization.tenantId(),
                organization.name(),
                organization.slug(),
                organization.plan(),
                organization.status(),
                organization.createdAt(),
                organization.updatedAt());
    }
}
