package dev.vericov.upload.adapter.storage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class HttpSupabaseObjectStorageClient implements SupabaseObjectStorageClient {
    private static final String CACHE_CONTROL = "3600";

    private final URI storageBaseUri;
    private final String serviceRoleKey;
    private final HttpClient httpClient;

    public HttpSupabaseObjectStorageClient(URI storageBaseUri, String serviceRoleKey) {
        this(storageBaseUri, serviceRoleKey, HttpClient.newHttpClient());
    }

    HttpSupabaseObjectStorageClient(URI storageBaseUri, String serviceRoleKey, HttpClient httpClient) {
        this.storageBaseUri = normalizeBaseUri(storageBaseUri);
        this.serviceRoleKey = requireSecret(serviceRoleKey);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public void upload(String bucket, String objectPath, String contentType, byte[] content) {
        HttpRequest request = HttpRequest.newBuilder(uploadUri(bucket, objectPath))
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("Content-Type", contentType)
                .header("cache-control", CACHE_CONTROL)
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(Arrays.copyOf(content, content.length)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new IllegalStateException(
                        "Supabase Storage upload failed with HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Supabase Storage upload failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Supabase Storage upload was interrupted", exception);
        }
    }

    URI uploadUri(String bucket, String objectPath) {
        return storageBaseUri.resolve("object/" + encodePath(bucket) + "/" + encodePath(objectPath));
    }

    private static URI normalizeBaseUri(URI uri) {
        Objects.requireNonNull(uri, "storageBaseUri");
        String value = uri.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static String requireSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("serviceRoleKey is required");
        }
        return value;
    }

    private static String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(HttpSupabaseObjectStorageClient::encodeSegment)
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
