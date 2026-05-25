package dev.vericov.analysis.adapter.storage;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpSupabaseArtifactContentStoreIntegrationTest {

    @Test
    void downloadsArtifactContentFromSupabaseStorageEndpoint() throws IOException {
        RecordedRequest recorded = new RecordedRequest();
        HttpServer server = serverReturning(recorded, 200, "TN:\n");
        try {
            HttpSupabaseArtifactContentStore store = new HttpSupabaseArtifactContentStore(
                    baseUri(server),
                    "service-role-key");

            byte[] content = store.read("coverage-raw", "tenant-id/upload-id/coverage/lcov report.info");

            assertArrayEquals("TN:\n".getBytes(StandardCharsets.UTF_8), content);
            assertEquals("GET", recorded.method);
            assertEquals(
                    "/storage/v1/object/authenticated/coverage-raw/tenant-id/upload-id/coverage/lcov%20report.info",
                    recorded.rawPath);
            assertEquals("service-role-key", recorded.header("apikey"));
            assertEquals("Bearer service-role-key", recorded.header("Authorization"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsNonSuccessfulStorageDownloadResponses() throws IOException {
        HttpServer server = serverReturning(new RecordedRequest(), 404, "");
        try {
            HttpSupabaseArtifactContentStore store = new HttpSupabaseArtifactContentStore(
                    baseUri(server),
                    "service-role-key");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> store.read("coverage-raw", "tenant/upload/missing.info"));

            assertEquals("Supabase Storage download failed with HTTP 404", exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer serverReturning(RecordedRequest recorded, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            recorded.method = exchange.getRequestMethod();
            recorded.rawPath = exchange.getRequestURI().getRawPath();
            recorded.headers = exchange.getRequestHeaders();
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

    private static final class RecordedRequest {
        private String method;
        private String rawPath;
        private com.sun.net.httpserver.Headers headers;

        private String header(String name) {
            return headers.getFirst(name);
        }
    }
}
