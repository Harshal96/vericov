package dev.vericov.analysis.adapter.storage;

import dev.vericov.analysis.application.port.NormalizedCoverageLocation;
import dev.vericov.analysis.application.port.NormalizedCoverageStore;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.NormalizedCoverageMapSerializer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class SupabaseNormalizedCoverageStore implements NormalizedCoverageStore {
    private static final String CACHE_CONTROL = "3600";
    private static final String CONTENT_TYPE = "application/gzip";

    private final URI storageBaseUri;
    private final String serviceRoleKey;
    private final String bucket;
    private final NormalizedCoverageMapSerializer serializer;
    private final HttpClient httpClient;

    public SupabaseNormalizedCoverageStore(
            URI storageBaseUri,
            String serviceRoleKey,
            String bucket,
            NormalizedCoverageMapSerializer serializer) {
        this(storageBaseUri, serviceRoleKey, bucket, serializer, HttpClient.newHttpClient());
    }

    SupabaseNormalizedCoverageStore(
            URI storageBaseUri,
            String serviceRoleKey,
            String bucket,
            NormalizedCoverageMapSerializer serializer,
            HttpClient httpClient) {
        this.storageBaseUri = normalizeBaseUri(storageBaseUri);
        this.serviceRoleKey = requireSecret(serviceRoleKey);
        this.bucket = requireValue(bucket, "bucket");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public NormalizedCoverageLocation store(CoverageReport report) {
        Objects.requireNonNull(report, "report");
        String path = objectPath(report);
        byte[] content = serializer.serialize(report);
        HttpRequest request = HttpRequest.newBuilder(uploadUri(bucket, path))
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("Content-Type", CONTENT_TYPE)
                .header("cache-control", CACHE_CONTROL)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(Arrays.copyOf(content, content.length)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new IllegalStateException(
                        "Supabase Storage upload failed with HTTP " + response.statusCode() + ": " + response.body());
            }
            return new NormalizedCoverageLocation(bucket, path);
        } catch (IOException exception) {
            throw new IllegalStateException("Supabase Storage upload failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Supabase Storage upload was interrupted", exception);
        }
    }

    URI uploadUri(String bucket, String storagePath) {
        return storageBaseUri.resolve("object/" + encodePath(bucket) + "/" + encodePath(storagePath));
    }

    private static String objectPath(CoverageReport report) {
        return report.tenantId() + "/" + report.uploadId() + "/coverage-normalized/coverage-map.json.gz";
    }

    private static URI normalizeBaseUri(URI uri) {
        Objects.requireNonNull(uri, "storageBaseUri");
        String value = uri.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static String requireSecret(String value) {
        return requireValue(value, "serviceRoleKey");
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(SupabaseNormalizedCoverageStore::encodeSegment)
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
