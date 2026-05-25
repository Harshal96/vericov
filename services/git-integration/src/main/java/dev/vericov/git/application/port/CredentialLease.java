package dev.vericov.git.application.port;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class CredentialLease {
    private final UUID leaseId;
    private final String credentialKind;
    private final char[] secret;
    private final Instant expiresAt;

    public CredentialLease(UUID leaseId, String credentialKind, char[] secret, Instant expiresAt) {
        this.leaseId = Objects.requireNonNull(leaseId, "leaseId");
        this.credentialKind = requireCanonical(credentialKind, "credentialKind");
        this.secret = Arrays.copyOf(Objects.requireNonNull(secret, "secret"), secret.length);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public UUID leaseId() {
        return leaseId;
    }

    public String credentialKind() {
        return credentialKind;
    }

    public char[] secret() {
        return Arrays.copyOf(secret, secret.length);
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "CredentialLease[leaseId=" + leaseId
                + ", credentialKind=" + credentialKind
                + ", secret=<redacted>, expiresAt=" + expiresAt + "]";
    }

    private static String requireCanonical(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
