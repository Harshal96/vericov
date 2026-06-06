package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CreateMembershipCommand;
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
