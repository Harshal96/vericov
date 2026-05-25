package dev.vericov.upload.adapter.auth;

import dev.vericov.upload.application.InvalidUploadException;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GithubActionsOidcVerifier {
    private final URI jwksUri;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Map<String, RSAPublicKey> keysById = new ConcurrentHashMap<>();

    public GithubActionsOidcVerifier(URI jwksUri, Clock clock) {
        this(jwksUri, clock, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    GithubActionsOidcVerifier(URI jwksUri, Clock clock, HttpClient httpClient) {
        this.jwksUri = jwksUri;
        this.clock = clock;
        this.httpClient = httpClient;
    }

    public JsonObject verify(String token, String issuer, String audience) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw unauthorized();
        }
        JsonObject header = parseJson(parts[0]);
        if (!"RS256".equals(UploadJwtSupport.stringClaim(header, "alg"))) {
            throw unauthorized();
        }
        String keyId = UploadJwtSupport.stringClaim(header, "kid");
        if (keyId == null) {
            throw unauthorized();
        }
        RSAPublicKey key = keysById.computeIfAbsent(keyId, this::fetchKey);
        verifySignature(key, parts[0] + "." + parts[1], parts[2]);
        JsonObject payload = UploadJwtSupport.parseUnverifiedPayload(token);
        UploadJwtSupport.validateRegisteredClaims(payload, issuer, audience, clock);
        return payload;
    }

    private RSAPublicKey fetchKey(String keyId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(jwksUri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw unauthorized();
            }
            JsonObject jwks = Json.createReader(new StringReader(response.body())).readObject();
            JsonArray keys = jwks.getJsonArray("keys");
            if (keys == null) {
                throw unauthorized();
            }
            for (var value : keys) {
                JsonObject key = value.asJsonObject();
                if (keyId.equals(UploadJwtSupport.stringClaim(key, "kid"))
                        && "RSA".equals(UploadJwtSupport.stringClaim(key, "kty"))) {
                    return rsaKey(key);
                }
            }
            throw unauthorized();
        } catch (InvalidUploadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    private static RSAPublicKey rsaKey(JsonObject key) throws Exception {
        BigInteger modulus = new BigInteger(1, decode(UploadJwtSupport.stringClaim(key, "n")));
        BigInteger exponent = new BigInteger(1, decode(UploadJwtSupport.stringClaim(key, "e")));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private static void verifySignature(RSAPublicKey key, String signingInput, String encodedSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] actual = decode(encodedSignature);
            if (!signature.verify(actual)) {
                throw unauthorized();
            }
        } catch (InvalidUploadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    private static JsonObject parseJson(String encoded) {
        try (var reader = Json.createReader(new StringReader(new String(decode(encoded), StandardCharsets.UTF_8)))) {
            return reader.readObject();
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
    }

    private static byte[] decode(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
    }

    private static InvalidUploadException unauthorized() {
        return new InvalidUploadException("unauthorized", "Invalid upload credential");
    }
}
