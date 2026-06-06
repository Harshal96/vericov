package dev.vericov.analysis.adapter.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.NormalizedCoverageMapSerializer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemArtifactStoresTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPOSITORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID UPLOAD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REPORT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @TempDir
    Path root;

    @Test
    void readsArtifactWrittenByUploadServiceLayout() throws Exception {
        Path artifact = root.resolve("coverage-raw")
                .resolve(TENANT_ID.toString())
                .resolve(UPLOAD_ID.toString())
                .resolve("coverage")
                .resolve("coverage.lcov");
        Files.createDirectories(artifact.getParent());
        byte[] content = "TN:\nend_of_record\n".getBytes(StandardCharsets.UTF_8);
        Files.write(artifact, content);

        assertArrayEquals(
                content,
                new FileSystemArtifactContentStore(root).read(
                        "coverage-raw",
                        TENANT_ID + "/" + UPLOAD_ID + "/coverage/coverage.lcov"));
    }

    @Test
    void rejectsLocationsOutsideStorageRoot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileSystemArtifactContentStore(root).read("coverage-raw", "../../secret"));
    }

    @Test
    void storesNormalizedCoverageInSharedFilesystemLayout() throws Exception {
        var location = new FileSystemNormalizedCoverageStore(
                        root,
                        "coverage-normalized",
                        new NormalizedCoverageMapSerializer())
                .store(report());

        assertEquals("coverage-normalized", location.bucket());
        assertEquals(
                TENANT_ID + "/" + UPLOAD_ID + "/coverage-normalized/coverage-map.json.gz",
                location.path());
        assertTrue(Files.readAllBytes(root.resolve(location.bucket()).resolve(location.path())).length > 0);
    }

    private static CoverageReport report() {
        CoverageMetric lines = new CoverageMetric(1, 1);
        CoverageMetric empty = new CoverageMetric(0, 0);
        return new CoverageReport(
                REPORT_ID,
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                null,
                lines,
                empty,
                empty,
                lines,
                List.of(new CoverageFileSummary("src/App.java", lines, empty, empty, lines)),
                List.of(new CoverageLineHit("src/App.java", 1, 1)),
                Instant.parse("2026-06-05T12:00:00Z"));
    }
}
