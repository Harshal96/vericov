package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoCoverProfileParserTest {
    @Test
    void parsesLineAndStatementCoverageFromCoverProfile() {
        GoCoverProfileParser parser = new GoCoverProfileParser();
        byte[] content = """
                mode: atomic
                github.com/acme/app/foo.go:10.1,10.20 2 1
                github.com/acme/app/foo.go:11.1,12.2 1 0
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("cover.out", "coverage", "go-coverprofile", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("github.com/acme/app/foo.go", file.filePath());
        assertEquals(new CoverageMetric(1, 3), file.line());
        assertEquals(new CoverageMetric(2, 3), file.statement());
        assertEquals(new CoverageMetric(0, 0), file.branch());
        assertEquals(new CoverageMetric(0, 0), file.function());
    }
}
