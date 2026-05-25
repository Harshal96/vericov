package dev.vericov.integrations.api;

import dev.vericov.integrations.application.CredentialLease;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class CredentialLeaseHttpResponse {
    private final String secretRef;
    private final String secret;
    private final Instant expiresAt;

    public CredentialLeaseHttpResponse(String secretRef, char[] secret, Instant expiresAt) {
        this.secretRef = Objects.requireNonNull(secretRef, "secretRef");
        char[] secretCopy = Arrays.copyOf(Objects.requireNonNull(secret, "secret"), secret.length);
        this.secret = new String(secretCopy);
        Arrays.fill(secretCopy, '\0');
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static CredentialLeaseHttpResponse from(CredentialLease lease) {
        Objects.requireNonNull(lease, "lease");
        return new CredentialLeaseHttpResponse(
                lease.secretRef(),
                lease.secret(),
                lease.expiresAt());
    }

    public String secretRef() {
        return secretRef;
    }

    @JsonbProperty("secret_ref")
    public String getSecretRef() {
        return secretRef;
    }

    public String secret() {
        return secret;
    }

    public String getSecret() {
        return secret;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @JsonbProperty("expires_at")
    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "CredentialLeaseHttpResponse[secretRef=" + secretRef
                + ", secret=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
