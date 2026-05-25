package dev.vericov.analysis.coverage;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizedCoverageMapSerializerTest {
    private static final UUID REPORT_ID = UUID.fromString("7a36b5bc-6bd2-44a2-bc8f-c886b809cf4d");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final Instant GENERATED_AT = Instant.parse("2026-05-23T12:00:00Z");

    @Test
    void serializesGzippedStableVersionedCoverageMap() throws Exception {
        NormalizedCoverageMapSerializer serializer = new NormalizedCoverageMapSerializer();

        JsonObject payload = decompressJson(serializer.serialize(report()));

        assertEquals(1, payload.getInt("schema_version"));
        JsonObject report = payload.getJsonObject("report");
        assertEquals(REPORT_ID.toString(), report.getString("id"));
        assertEquals(UPLOAD_ID.toString(), report.getString("upload_id"));
        assertEquals(TENANT_ID.toString(), report.getString("tenant_id"));
        assertEquals(REPOSITORY_ID.toString(), report.getString("repository_id"));
        assertEquals("abc123", report.getString("commit_sha"));
        assertEquals("main", report.getString("branch"));
        assertEquals(42, report.getInt("pull_request_number"));
        assertEquals(GENERATED_AT.toString(), report.getString("generated_at"));

        JsonObject totals = payload.getJsonObject("totals");
        assertMetric(totals.getJsonObject("line"), 3, 5);
        assertMetric(totals.getJsonObject("branch"), 1, 2);
        assertMetric(totals.getJsonObject("function"), 1, 1);
        assertMetric(totals.getJsonObject("statement"), 3, 5);

        var files = payload.getJsonArray("files");
        assertEquals("src/A.java", files.getJsonObject(0).getString("path"));
        assertEquals("src/B.java", files.getJsonObject(1).getString("path"));

        var aLineHits = files.getJsonObject(0).getJsonArray("line_hits");
        assertEquals(1, aLineHits.getJsonObject(0).getInt("line"));
        assertEquals(3, aLineHits.getJsonObject(0).getInt("hits"));
        assertEquals(2, aLineHits.getJsonObject(1).getInt("line"));
        assertEquals(0, aLineHits.getJsonObject(1).getInt("hits"));
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
                new CoverageMetric(3, 5),
                new CoverageMetric(1, 2),
                new CoverageMetric(1, 1),
                new CoverageMetric(3, 5),
                List.of(
                        new CoverageFileSummary(
                                "src/B.java",
                                new CoverageMetric(1, 2),
                                new CoverageMetric(0, 0),
                                new CoverageMetric(0, 0),
                                new CoverageMetric(1, 2)),
                        new CoverageFileSummary(
                                "src/A.java",
                                new CoverageMetric(2, 3),
                                new CoverageMetric(1, 2),
                                new CoverageMetric(1, 1),
                                new CoverageMetric(2, 3))),
                List.of(
                        new CoverageLineHit("src/A.java", 2, 0),
                        new CoverageLineHit("src/B.java", 8, 1),
                        new CoverageLineHit("src/A.java", 1, 3),
                        new CoverageLineHit("src/B.java", 9, 0)),
                GENERATED_AT);
    }

    private static JsonObject decompressJson(byte[] compressed) throws Exception {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                var reader = Json.createReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            return reader.readObject();
        }
    }

    private static void assertMetric(JsonObject metric, int covered, int total) {
        assertEquals(covered, metric.getInt("covered"));
        assertEquals(total, metric.getInt("total"));
    }
}
