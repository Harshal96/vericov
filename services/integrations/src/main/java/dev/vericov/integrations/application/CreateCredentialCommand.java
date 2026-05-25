package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public final class CreateCredentialCommand {
    private final UUID requesterUserId;
    private final UUID tenantId;
    private final UUID orgId;
    private final UUID connectionId;
    private final String credentialKind;
    private final char[] secret;
    private final Instant expiresAt;

    public CreateCredentialCommand(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind,
            char[] secret,
            Instant expiresAt) {
        this.requesterUserId = requesterUserId;
        this.tenantId = tenantId;
        this.orgId = orgId;
        this.connectionId = connectionId;
        this.credentialKind = credentialKind;
        this.secret = copy(secret);
        this.expiresAt = expiresAt;
    }

    public UUID requesterUserId() {
        return requesterUserId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID orgId() {
        return orgId;
    }

    public UUID connectionId() {
        return connectionId;
    }

    public String credentialKind() {
        return credentialKind;
    }

    public char[] secret() {
        return copy(secret);
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    private static char[] copy(char[] secret) {
        return secret == null ? null : Arrays.copyOf(secret, secret.length);
    }
}
