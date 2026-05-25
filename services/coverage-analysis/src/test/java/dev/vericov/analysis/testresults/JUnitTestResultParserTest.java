package dev.vericov.analysis.testresults;

import dev.vericov.analysis.coverage.CoverageInputArtifact;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JUnitTestResultParserTest {
    private final JUnitTestResultParser parser = new JUnitTestResultParser(new SecureXmlTestResultDocumentReader());

    @Test
    void parsesSingleJUnitSuiteWithAggregateAttributes() {
        List<ParsedTestRun> runs = parse("""
                <testsuite name="unit" tests="4" failures="1" errors="0" skipped="1" time="1.234">
                  <testcase classname="AppTest" name="passes" time="0.1" />
                  <testcase classname="AppTest" name="fails" time="0.2"><failure>boom</failure></testcase>
                  <testcase classname="AppTest" name="skips"><skipped /></testcase>
                  <testcase classname="AppTest" name="passes-too" />
                </testsuite>
                """);

        assertEquals(1, runs.size());
        ParsedTestRun run = runs.getFirst();
        assertEquals("unit", run.suiteName());
        assertEquals(0, run.suiteIndex());
        assertEquals("failed", run.status());
        assertEquals(4, run.totalCount());
        assertEquals(2, run.passedCount());
        assertEquals(1, run.failedCount());
        assertEquals(0, run.errorCount());
        assertEquals(1, run.skippedCount());
        assertEquals(1234L, run.durationMs());
    }

    @Test
    void parsesTestsuitesRootIntoOneRunPerSuite() {
        List<ParsedTestRun> runs = parse("""
                <testsuites>
                  <testsuite name="unit" tests="1" failures="0" errors="0" skipped="0" time="0.010" />
                  <testsuite name="integration" tests="2" failures="0" errors="1" skipped="0" time="2.5" />
                </testsuites>
                """);

        assertEquals(2, runs.size());
        assertEquals("unit", runs.get(0).suiteName());
        assertEquals("passed", runs.get(0).status());
        assertEquals(10L, runs.get(0).durationMs());
        assertEquals("integration", runs.get(1).suiteName());
        assertEquals("error", runs.get(1).status());
        assertEquals(2500L, runs.get(1).durationMs());
    }

    @Test
    void parsesNestedTestsuites() {
        List<ParsedTestRun> runs = parse("""
                <testsuites>
                  <testsuite name="parent" tests="2" failures="0" errors="0" skipped="0" time="0.3">
                    <testsuite name="child" tests="1" failures="0" errors="0" skipped="0" time="0.1" />
                  </testsuite>
                </testsuites>
                """);

        assertEquals(2, runs.size());
        assertEquals("parent", runs.get(0).suiteName());
        assertEquals(300L, runs.get(0).durationMs());
        assertEquals("child", runs.get(1).suiteName());
        assertEquals(100L, runs.get(1).durationMs());
    }

    @Test
    void computesCountsAndDurationFromTestCasesWhenSuiteAttributesAreMissing() {
        List<ParsedTestRun> runs = parse("""
                <testsuite name="computed">
                  <testcase name="pass" time="0.1" />
                  <testcase name="fail" time="0.2"><failure /></testcase>
                  <testcase name="error" time="0.3"><error /></testcase>
                  <testcase name="skip" time="0.4"><skipped /></testcase>
                </testsuite>
                """);

        ParsedTestRun run = runs.getFirst();
        assertEquals("error", run.status());
        assertEquals(4, run.totalCount());
        assertEquals(1, run.passedCount());
        assertEquals(1, run.failedCount());
        assertEquals(1, run.errorCount());
        assertEquals(1, run.skippedCount());
        assertEquals(1000L, run.durationMs());
    }

    @Test
    void marksAllSkippedSuiteAsSkipped() {
        ParsedTestRun run = parse("""
                <testsuite name="skipped" tests="2" failures="0" errors="0" skipped="2" />
                """).getFirst();

        assertEquals("skipped", run.status());
        assertEquals(0L, run.durationMs());
    }

    @Test
    void rejectsInvalidXmlAndDoctypeDeclarations() {
        CoverageInputArtifact artifact = artifact("junit.xml", "junit");

        IllegalStateException invalid = assertThrows(
                IllegalStateException.class,
                () -> parser.parse(artifact, "<testsuite>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(true, invalid.getMessage().contains("Invalid XML test result artifact junit.xml"));

        IllegalStateException xxe = assertThrows(
                IllegalStateException.class,
                () -> parser.parse(artifact, """
                        <!DOCTYPE testsuite [
                          <!ENTITY xxe SYSTEM "file:///etc/passwd">
                        ]>
                        <testsuite name="bad" tests="1">&xxe;</testsuite>
                        """.getBytes(StandardCharsets.UTF_8)));
        assertEquals(true, xxe.getMessage().contains("Invalid XML test result artifact junit.xml"));
    }

    @Test
    void rejectsUnsupportedRootAndInvalidCounts() {
        IllegalStateException unsupported = assertThrows(
                IllegalStateException.class,
                () -> parse("<report />"));
        assertEquals("Unsupported JUnit XML root element: report for junit.xml", unsupported.getMessage());

        IllegalStateException invalidCounts = assertThrows(
                IllegalStateException.class,
                () -> parse("""
                        <testsuite name="bad" tests="1" failures="1" errors="1" skipped="0" />
                        """));
        assertEquals("Invalid JUnit test counts for suite bad in junit.xml", invalidCounts.getMessage());
    }

    private List<ParsedTestRun> parse(String xml) {
        return parser.parse(artifact("junit.xml", "junit"), xml.getBytes(StandardCharsets.UTF_8));
    }

    private static CoverageInputArtifact artifact(String name, String format) {
        return new CoverageInputArtifact(name, "test_results", format, "test-results-raw", "tenant/upload/" + name, "sha-1");
    }
}
