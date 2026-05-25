package dev.vericov.organization.api;

import dev.vericov.organization.application.BadgeTokenDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record BadgeTokenHttpResponse(
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String token,
        @JsonbProperty("token_prefix")
        String tokenPrefix,
        @JsonbProperty("rotated_at")
        Instant rotatedAt) {

    public static BadgeTokenHttpResponse from(BadgeTokenDetails details) {
        return new BadgeTokenHttpResponse(
                details.organizationId(),
                details.repositoryId(),
                details.token(),
                details.tokenPrefix(),
                details.rotatedAt());
    }
}
