package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.MembershipDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record MembershipHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("supabase_user_id")
        UUID supabaseUserId,
        String role,
        String status,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static MembershipHttpResponse from(MembershipDetails membership) {
        return new MembershipHttpResponse(
                membership.id(),
                membership.tenantId(),
                membership.organizationId(),
                membership.supabaseUserId(),
                membership.role(),
                membership.status(),
                membership.createdAt(),
                membership.updatedAt());
    }
}
