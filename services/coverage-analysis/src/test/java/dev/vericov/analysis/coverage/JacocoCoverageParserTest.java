package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacocoCoverageParserTest {
    @Test
    void parsesSourcefileLinesBranchesMethodsAndStatements() {
        JacocoCoverageParser parser = new JacocoCoverageParser(new SecureXmlCoverageDocumentReader());
        byte[] content = """
                <report name="unit">
                  <package name="dev/vericov">
                    <class name="dev/vericov/App" sourcefilename="App.java">
                      <method name="hit" desc="()V" line="10">
                        <counter type="METHOD" missed="0" covered="1" />
                      </method>
                      <method name="miss" desc="()V" line="20">
                        <counter type="METHOD" missed="1" covered="0" />
                      </method>
                    </class>
                    <sourcefile name="App.java">
                      <line nr="10" mi="0" ci="4" mb="1" cb="1" />
                      <line nr="20" mi="2" ci="0" mb="0" cb="0" />
                    </sourcefile>
                  </package>
                </report>
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("jacoco.xml", "coverage", "jacoco", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("dev/vericov/App.java", file.filePath());
        assertEquals(new CoverageMetric(1, 2), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 2), file.function());
        assertEquals(new CoverageMetric(1, 2), file.statement());
    }
}
