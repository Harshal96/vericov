package dev.vericov.analysis.adapter.git;

import com.sun.net.httpserver.HttpServer;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffLineType;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalGitDiffHttpClientTest {

    @Test
    void fetchesInternalGitDiffWithHeadShaAndParsesLineMetadata() throws IOException {
        RecordedRequest recorded = new RecordedRequest();
        HttpServer server = serverReturning(recorded, """
                {
                  "data": {
                    "repository_id": "44444444-4444-4444-4444-444444444444",
                    "pull_request_number": 42,
                    "base_sha": "base123",
                    "head_sha": "head456",
                    "files": [
                      {
                        "file_path": "src/App.java",
                        "old_file_path": null,
                        "change_status": "modified",
                        "lines": [
                          {
                            "base_line_number": null,
                            "head_line_number": 10,
                            "change_type": "added"
                          },
                          {
                            "base_line_number": 30,
                            "head_line_number": 31,
                            "change_type": "context"
                          }
                        ]
                      }
                    ]
                  }
                }
                """);
        try {
            var client = new InternalGitDiffHttpClient(
                    baseUri(server),
                    "service-token");

            var diff = client.fetch(input(), report());

            assertEquals("/internal/v1/git/repositories/44444444-4444-4444-4444-444444444444/pull-requests/42/diff",
                    recorded.rawPath);
            assertEquals(
                    "tenant_id=33333333-3333-3333-3333-333333333333"
                            + "&org_id=22222222-2222-2222-2222-222222222222"
                            + "&provider=github"
                            + "&head_sha=head456",
                    recorded.rawQuery);
            assertEquals("coverage-analysis", recorded.header("X-Vericov-Service-Name"));
            assertEquals("service-token", recorded.header("X-Vericov-Service-Token"));
            assertEquals("base123", diff.baseSha());
            assertEquals("head456", diff.headSha());
            assertEquals("src/App.java", diff.files().getFirst().filePath());
            assertEquals(DiffLineType.ADDED, diff.files().getFirst().lines().getFirst().type());
            assertEquals(DiffLineType.CONTEXT, diff.files().getFirst().lines().get(1).type());
        } finally {
            server.stop(0);
        }
    }

    private static CoverageAnalysisInput input() {
        return new CoverageAnalysisInput(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "github",
                "head456",
                "feature",
                42,
                List.of());
    }

    private static CoverageReport report() {
        return new CoverageReport(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "head456",
                "feature",
                42,
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                List.of(),
                List.of(),
                Instant.parse("2026-05-23T10:00:00Z"));
    }

    private static HttpServer serverReturning(RecordedRequest recorded, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            recorded.rawPath = exchange.getRequestURI().getRawPath();
            recorded.rawQuery = exchange.getRequestURI().getRawQuery();
            recorded.headers = exchange.getRequestHeaders();
            byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static URI baseUri(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static final class RecordedRequest {
        private String rawPath;
        private String rawQuery;
        private com.sun.net.httpserver.Headers headers;

        private String header(String name) {
            return headers.getFirst(name);
        }
    }
}
