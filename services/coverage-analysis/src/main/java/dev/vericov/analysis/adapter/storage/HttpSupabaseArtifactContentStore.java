package dev.vericov.analysis.adapter.storage;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class HttpSupabaseArtifactContentStore implements ArtifactContentStore {
    private final URI storageBaseUri;
    private final String serviceRoleKey;
    private final HttpClient httpClient;

    public HttpSupabaseArtifactContentStore(URI storageBaseUri, String serviceRoleKey) {
        this(storageBaseUri, serviceRoleKey, HttpClient.newHttpClient());
    }

    HttpSupabaseArtifactContentStore(URI storageBaseUri, String serviceRoleKey, HttpClient httpClient) {
        this.storageBaseUri = normalizeBaseUri(storageBaseUri);
        this.serviceRoleKey = requireSecret(serviceRoleKey);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public byte[] read(String bucket, String storagePath) {
        HttpRequest request = HttpRequest.newBuilder(downloadUri(bucket, storagePath))
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new IllegalStateException(
                        "Supabase Storage download failed with HTTP " + response.statusCode());
            }
            return Arrays.copyOf(response.body(), response.body().length);
        } catch (IOException exception) {
            throw new IllegalStateException("Supabase Storage download failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Supabase Storage download was interrupted", exception);
        }
    }

    URI downloadUri(String bucket, String storagePath) {
        return storageBaseUri.resolve("object/authenticated/" + encodePath(bucket) + "/" + encodePath(storagePath));
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
                .map(HttpSupabaseArtifactContentStore::encodeSegment)
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
