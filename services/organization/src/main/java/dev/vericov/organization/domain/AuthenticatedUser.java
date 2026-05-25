package dev.vericov.organization.domain;

import java.util.Objects;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email) {
    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public AuthenticatedUser(UUID userId) {
        this(userId, null);
    }
}
