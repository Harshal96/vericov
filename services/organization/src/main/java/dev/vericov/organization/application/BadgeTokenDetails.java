package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BadgeTokenDetails(
        UUID organizationId,
        UUID repositoryId,
        String token,
        String tokenPrefix,
        Instant rotatedAt) {

    public BadgeTokenDetails {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(tokenPrefix, "tokenPrefix");
        Objects.requireNonNull(rotatedAt, "rotatedAt");
    }
}
