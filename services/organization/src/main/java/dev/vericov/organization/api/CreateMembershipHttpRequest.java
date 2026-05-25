package dev.vericov.organization.api;

import dev.vericov.organization.application.CreateMembershipCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record CreateMembershipHttpRequest(
        @JsonbProperty("supabase_user_id")
        UUID supabaseUserId,
        String role,
        String status) {

    public CreateMembershipCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new CreateMembershipCommand(requesterUserId, organizationId, supabaseUserId, role, status);
    }
}
