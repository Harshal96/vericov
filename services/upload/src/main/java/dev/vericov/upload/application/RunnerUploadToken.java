package dev.vericov.upload.application;

import java.time.Instant;

public record RunnerUploadToken(
        String token,
        Instant expiresAt) {
}
