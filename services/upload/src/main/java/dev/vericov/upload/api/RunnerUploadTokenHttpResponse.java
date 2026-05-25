package dev.vericov.upload.api;

import dev.vericov.upload.application.RunnerUploadToken;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;

public record RunnerUploadTokenHttpResponse(
        String token,
        @JsonbProperty("expires_at")
        Instant expiresAt) {

    public static RunnerUploadTokenHttpResponse from(RunnerUploadToken token) {
        return new RunnerUploadTokenHttpResponse(token.token(), token.expiresAt());
    }
}
