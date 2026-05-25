package dev.vericov.upload.adapter.storage;

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

class HttpSupabaseObjectStorageClientIntegrationTest {

    @Test
    void uploadsObjectToSupabaseStorageEndpoint() throws IOException {
        RecordedRequest recorded = new RecordedRequest();
        HttpServer server = serverReturning(recorded, 200, "");
        try {
            HttpSupabaseObjectStorageClient client = new HttpSupabaseObjectStorageClient(
                    baseUri(server),
                    "service-role-key");

            client.upload(
                    "coverage-raw",
                    "tenant-id/upload-id/coverage/lcov report.info",
                    "text/plain",
                    "TN:\n".getBytes(StandardCharsets.UTF_8));

            assertEquals("POST", recorded.method);
            assertEquals(
                    "/storage/v1/object/coverage-raw/tenant-id/upload-id/coverage/lcov%20report.info",
                    recorded.rawPath);
            assertEquals("service-role-key", recorded.header("apikey"));
            assertEquals("Bearer service-role-key", recorded.header("Authorization"));
            assertEquals("text/plain", recorded.header("Content-Type"));
            assertEquals("3600", recorded.header("cache-control"));
            assertEquals("false", recorded.header("x-upsert"));
            assertArrayEquals("TN:\n".getBytes(StandardCharsets.UTF_8), recorded.body);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsNonSuccessfulStorageUploadResponses() throws IOException {
        HttpServer server = serverReturning(new RecordedRequest(), 503, "storage unavailable");
        try {
            HttpSupabaseObjectStorageClient client = new HttpSupabaseObjectStorageClient(
                    baseUri(server),
                    "service-role-key");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> client.upload("coverage-raw", "tenant/upload/lcov.info", "text/plain", new byte[] {1}));

            assertEquals(
                    "Supabase Storage upload failed with HTTP 503: storage unavailable",
                    exception.getMessage());
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

    private static final class RecordedRequest {
        private String method;
        private String rawPath;
        private com.sun.net.httpserver.Headers headers;
        private byte[] body = new byte[0];

        private String header(String name) {
            return headers.getFirst(name);
        }
    }
}
