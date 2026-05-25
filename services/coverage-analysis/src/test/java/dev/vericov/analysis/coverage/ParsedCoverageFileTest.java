package dev.vericov.analysis.coverage;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParsedCoverageFileTest {
    @Test
    void statementMetricCanDifferFromLineMetric() {
        ParsedCoverageFile file = new ParsedCoverageFile(
                "src/App.java",
                Set.of(10),
                Set.of(10),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("10:0", "10:1"),
                Set.of("10:0"));

        assertEquals(new CoverageMetric(1, 1), file.line());
        assertEquals(new CoverageMetric(1, 2), file.statement());
    }
}
