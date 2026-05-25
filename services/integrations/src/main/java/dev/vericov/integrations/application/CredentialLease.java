package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class CredentialLease {
    private final String secretRef;
    private final char[] secret;
    private final Instant expiresAt;

    public CredentialLease(String secretRef, char[] secret, Instant expiresAt) {
        this.secretRef = IntegrationConfigValues.requireTrimmed(secretRef, "secretRef");
        this.secret = Arrays.copyOf(Objects.requireNonNull(secret, "secret"), secret.length);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public String secretRef() {
        return secretRef;
    }

    public char[] secret() {
        return Arrays.copyOf(secret, secret.length);
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
