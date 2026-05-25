package dev.vericov.upload.adapter.auth;

import dev.vericov.upload.application.RunnerUploadToken;
import dev.vericov.upload.application.port.RunnerUploadTokenIssuer;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import jakarta.json.Json;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacRunnerUploadTokenIssuer implements RunnerUploadTokenIssuer {
    private final byte[] secret;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public HmacRunnerUploadTokenIssuer(String secret, String issuer, String audience, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret is required");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.audience = audience;
        this.clock = clock;
    }

    @Override
    public RunnerUploadToken issue(RepositoryApiKeyPrincipal principal, UUID repositoryId, String branch, Duration ttl) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        String header = encodeJson(Json.createObjectBuilder()
                .add("alg", "HS256")
                .add("typ", "JWT")
                .build());
        var payloadBuilder = Json.createObjectBuilder()
                .add("iss", issuer)
                .add("aud", audience)
                .add("sub", "repository:" + repositoryId)
                .add("jti", UUID.randomUUID().toString())
                .add("iat", issuedAt.getEpochSecond())
                .add("nbf", issuedAt.getEpochSecond())
                .add("exp", expiresAt.getEpochSecond())
                .add("tenant_id", principal.tenantId().toString())
                .add("repository_id", repositoryId.toString())
                .add("branch", branch);
        if (principal.apiKeyId() != null) {
            payloadBuilder.add("api_key_id", principal.apiKeyId().toString());
        }
        var scopes = Json.createArrayBuilder();
        principal.scopes().forEach(scopes::add);
        payloadBuilder.add("scopes", scopes);
        var branchAllowPatterns = Json.createArrayBuilder();
        principal.allowedBranches().forEach(branchAllowPatterns::add);
        payloadBuilder.add("branch_allow_patterns", branchAllowPatterns);
        String payload = encodeJson(payloadBuilder.build());
        String signingInput = header + "." + payload;
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(signingInput));
        return new RunnerUploadToken(signingInput + "." + signature, expiresAt);
    }

    private static String encodeJson(jakarta.json.JsonObject object) {
        StringWriter writer = new StringWriter();
        Json.createWriter(writer).writeObject(object);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(writer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }
}
