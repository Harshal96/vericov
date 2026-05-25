package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloverCoverageParserTest {
    @Test
    void parsesFileLineConditionAndMethodCoverage() {
        CloverCoverageParser parser = new CloverCoverageParser(new SecureXmlCoverageDocumentReader());
        byte[] content = """
                <coverage generated="1">
                  <project>
                    <file path="./src/App.java" name="App.java">
                      <line num="10" type="method" name="run" count="1" />
                      <line num="11" type="stmt" count="0" />
                      <line num="12" type="cond" count="1" truecount="1" falsecount="0" />
                    </file>
                  </project>
                </coverage>
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("clover.xml", "coverage", "clover", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("src/App.java", file.filePath());
        assertEquals(new CoverageMetric(2, 3), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 1), file.function());
        assertEquals(new CoverageMetric(2, 3), file.statement());
    }
}
