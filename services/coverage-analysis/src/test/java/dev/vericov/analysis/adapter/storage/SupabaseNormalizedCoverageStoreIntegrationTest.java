package dev.vericov.analysis.adapter.storage;

import com.sun.net.httpserver.HttpServer;
import dev.vericov.analysis.application.port.NormalizedCoverageLocation;
import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.NormalizedCoverageMapSerializer;
import jakarta.json.Json;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupabaseNormalizedCoverageStoreIntegrationTest {
    private static final UUID REPORT_ID = UUID.fromString("7a36b5bc-6bd2-44a2-bc8f-c886b809cf4d");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");

    @Test
    void uploadsNormalizedCoverageMapWithDeterministicPath() throws Exception {
        RecordedRequest recorded = new RecordedRequest();
        HttpServer server = serverReturning(recorded, 200, "{}");
        try {
            SupabaseNormalizedCoverageStore store = new SupabaseNormalizedCoverageStore(
                    baseUri(server),
                    "service-role-key",
                    "coverage-normalized",
                    new NormalizedCoverageMapSerializer());

            NormalizedCoverageLocation location = store.store(report());

            String expectedPath = TENANT_ID + "/" + UPLOAD_ID + "/coverage-normalized/coverage-map.json.gz";
            assertEquals("coverage-normalized", location.bucket());
            assertEquals(expectedPath, location.path());
            assertEquals("POST", recorded.method);
            assertEquals("/storage/v1/object/coverage-normalized/" + expectedPath, recorded.rawPath);
            assertEquals("service-role-key", recorded.header("apikey"));
            assertEquals("Bearer service-role-key", recorded.header("Authorization"));
            assertEquals("application/gzip", recorded.header("Content-Type"));
            assertEquals("true", recorded.header("x-upsert"));
            assertEquals(REPORT_ID.toString(), recordedReportId(recorded.body));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsNonSuccessfulStorageUploadResponses() throws IOException {
        HttpServer server = serverReturning(new RecordedRequest(), 503, "unavailable");
        try {
            SupabaseNormalizedCoverageStore store = new SupabaseNormalizedCoverageStore(
                    baseUri(server),
                    "service-role-key",
                    "coverage-normalized",
                    new NormalizedCoverageMapSerializer());

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> store.store(report()));

            assertEquals("Supabase Storage upload failed with HTTP 503: unavailable", exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private static CoverageReport report() {
        return new CoverageReport(
                REPORT_ID,
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                new CoverageMetric(1, 1),
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                new CoverageMetric(1, 1),
                List.of(new CoverageFileSummary(
                        "src/App.java",
                        new CoverageMetric(1, 1),
                        new CoverageMetric(0, 0),
                        new CoverageMetric(0, 0),
                        new CoverageMetric(1, 1))),
                List.of(new CoverageLineHit("src/App.java", 1, 1)),
                Instant.parse("2026-05-23T12:00:00Z"));
    }

    private static HttpServer serverReturning(RecordedRequest recorded, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            recorded.method = exchange.getRequestMethod();
            recorded.rawPath = exchange.getRequestURI().getRawPath();
            recorded.headers = exchange.getRequestHeaders();
            recorded.body = exchange.getRequestBody().readAllBytes();
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static URI baseUri(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/storage/v1");
    }

    private static String recordedReportId(byte[] body) throws IOException {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(body));
                var reader = Json.createReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            return reader.readObject().getJsonObject("report").getString("id");
        }
    }

    private static final class RecordedRequest {
        private String method;
        private String rawPath;
        private com.sun.net.httpserver.Headers headers;
        private byte[] body;

        private String header(String name) {
            return headers.getFirst(name);
        }
    }
}
