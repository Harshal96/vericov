package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GcovCoverageParserTest {
    @Test
    void parsesLineBranchAndFunctionCoverageFromGcovText() {
        GcovCoverageParser parser = new GcovCoverageParser();
        byte[] content = """
                    -:    0:Source:src/main.c
                    1:   10:int main(void) {
                #####:   11:  if (flag) {
                branch  0 never executed
                    3:   12:  return 0;
                branch  0 taken 100%
                    -:   13:}
                function main called 1 returned 100% blocks executed 100%
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("main.c.gcov", "coverage", "gcov", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("src/main.c", file.filePath());
        assertEquals(new CoverageMetric(2, 3), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 1), file.function());
        assertEquals(new CoverageMetric(2, 3), file.statement());
    }
}
