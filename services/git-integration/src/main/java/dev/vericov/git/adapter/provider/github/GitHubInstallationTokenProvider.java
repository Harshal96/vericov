package dev.vericov.git.adapter.provider.github;

import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitProviderAction;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class GitHubInstallationTokenProvider implements GitHubProviderClient.AccessTokenProvider {
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION = "2022-11-28";

    private final URI baseUri;
    private final Clock clock;
    private final HttpTransport transport;

    public GitHubInstallationTokenProvider(URI baseUri, Clock clock) {
        this(baseUri, clock, new JavaHttpTransport(HttpClient.newHttpClient()));
    }

    public GitHubInstallationTokenProvider(URI baseUri, Clock clock, HttpTransport transport) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public String accessToken(GitProviderAction action) {
        Objects.requireNonNull(action, "action");
        String credentialKind = action.credentialKind().toLowerCase(Locale.ROOT);
        if ("github_installation_token".equals(credentialKind)
                || "oauth_access_token".equals(credentialKind)
                || "api_token".equals(credentialKind)) {
            return secretString(action);
        }
        if (!"github_app_private_key".equals(credentialKind)) {
            throw new GitIntegrationException("unsupported_credential", "Unsupported GitHub credential kind");
        }

        String installationId = requiredConfig(action, "installation_id");
        String appId = firstNonBlank(configValue(action.connectionConfig(), "app_id"), env("VERICOV_GITHUB_APP_ID"));
        if (appId == null) {
            throw new GitIntegrationException("validation_error", "GitHub app_id is required");
        }
        String jwt = githubAppJwt(appId, secretString(action));
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "/app/installations/" + encodePathSegment(installationId) + "/access_tokens"))
                .header("Accept", ACCEPT)
                .header("Authorization", "Bearer " + jwt)
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        try {
            HttpResult result = transport.send(request, "{}");
            if (result.statusCode() != 201) {
                throw new GitIntegrationException("provider_error", "GitHub installation token request failed");
            }
            String token = readToken(result.body());
            if (token == null || token.isBlank()) {
                throw new GitIntegrationException("provider_error", "GitHub installation token response is invalid");
            }
            return token;
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub installation token request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub installation token request interrupted", exception);
        }
    }

    private String githubAppJwt(String appId, String privateKeyPem) {
        long now = clock.instant().getEpochSecond();
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(("{" +
                "\"iat\":" + (now - 60) + "," +
                "\"exp\":" + (now + 540) + "," +
                "\"iss\":\"" + escapeJson(appId) + "\"" +
                "}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        byte[] signature = sign(signingInput.getBytes(StandardCharsets.UTF_8), privateKeyPem);
        return signingInput + "." + base64Url(signature);
    }

    private static byte[] sign(byte[] signingInput, String privateKeyPem) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(privateKeyPem));
            signature.update(signingInput);
            return signature.sign();
        } catch (GeneralSecurityException exception) {
            throw new GitIntegrationException("validation_error", "GitHub App private key is invalid");
        }
    }

    private static PrivateKey privateKey(String privateKeyPem) throws GeneralSecurityException {
        byte[] der = decodePem(privateKeyPem);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        try {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (InvalidKeySpecException exception) {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1RsaPrivateKey(der)));
        }
    }

    private static byte[] decodePem(String pem) {
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static byte[] wrapPkcs1RsaPrivateKey(byte[] pkcs1) {
        byte[] algorithmIdentifier = new byte[] {
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        ByteArrayOutputStream privateKey = new ByteArrayOutputStream();
        writeDerSequence(privateKey, version, algorithmIdentifier, derOctetString(pkcs1));
        return privateKey.toByteArray();
    }

    private static void writeDerSequence(ByteArrayOutputStream output, byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        output.write(0x30);
        writeDerLength(output, length);
        for (byte[] part : parts) {
            output.writeBytes(part);
        }
    }

    private static byte[] derOctetString(byte[] value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x04);
        writeDerLength(output, value.length);
        output.writeBytes(value);
        return output.toByteArray();
    }

    private static void writeDerLength(ByteArrayOutputStream output, int length) {
        if (length < 128) {
            output.write(length);
            return;
        }
        byte[] encoded = java.math.BigInteger.valueOf(length).toByteArray();
        output.write(0x80 | encoded.length);
        output.writeBytes(encoded);
    }

    private static String readToken(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body == null ? "{}" : body))) {
            JsonObject json = reader.readObject();
            return json.getString("token", null);
        }
    }

    private static String requiredConfig(GitProviderAction action, String key) {
        String value = firstNonBlank(
                configValue(action.connectionConfig(), key),
                configValue(action.bindingConfig(), key));
        if (value == null) {
            throw new GitIntegrationException("validation_error", "GitHub " + key + " is required");
        }
        return value;
    }

    private static String configValue(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String secretString(GitProviderAction action) {
        return new String(action.credentialLease().secret());
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public interface HttpTransport {
        HttpResult send(HttpRequest request, String body) throws IOException, InterruptedException;
    }

    public record HttpResult(int statusCode, String body) {
    }

    private record JavaHttpTransport(HttpClient httpClient) implements HttpTransport {
        @Override
        public HttpResult send(HttpRequest request, String body) throws IOException, InterruptedException {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        }
    }
}
