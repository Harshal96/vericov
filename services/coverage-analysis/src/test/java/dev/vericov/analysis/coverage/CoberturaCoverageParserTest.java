package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoberturaCoverageParserTest {
    @Test
    void parsesLineBranchFunctionAndStatementCoverage() {
        CoberturaCoverageParser parser = new CoberturaCoverageParser(new SecureXmlCoverageDocumentReader());
        byte[] content = """
                <coverage>
                  <packages>
                    <package name="app">
                      <classes>
                        <class name="App" filename="./src/App.py">
                          <methods>
                            <method name="hit" signature="()" line-rate="1.0">
                              <lines><line number="10" hits="1" /></lines>
                            </method>
                            <method name="miss" signature="()" line-rate="0.0">
                              <lines><line number="20" hits="0" /></lines>
                            </method>
                          </methods>
                          <lines>
                            <line number="10" hits="1" branch="true" condition-coverage="50% (1/2)" />
                            <line number="20" hits="0" branch="false" />
                          </lines>
                        </class>
                      </classes>
                    </package>
                  </packages>
                </coverage>
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("coverage.xml", "coverage", "cobertura", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("src/App.py", file.filePath());
        assertEquals(new CoverageMetric(1, 2), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 2), file.function());
        assertEquals(new CoverageMetric(1, 2), file.statement());
    }

    @Test
    void supportsCoveragePyXmlAlias() {
        CoberturaCoverageParser parser = new CoberturaCoverageParser(new SecureXmlCoverageDocumentReader());

        assertTrue(parser.supports(new CoverageInputArtifact(
                "coverage.xml",
                "coverage",
                "coverage.py-xml",
                "bucket",
                "path",
                "sha")));
    }
}
