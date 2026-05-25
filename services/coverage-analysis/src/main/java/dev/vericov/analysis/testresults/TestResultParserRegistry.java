package dev.vericov.analysis.testresults;

import dev.vericov.analysis.coverage.CoverageInputArtifact;
import java.util.List;
import java.util.Objects;

public class TestResultParserRegistry {
    private final List<TestResultParser> parsers;

    public TestResultParserRegistry(List<TestResultParser> parsers) {
        this.parsers = List.copyOf(Objects.requireNonNull(parsers, "parsers"));
    }

    public static TestResultParserRegistry empty() {
        return new TestResultParserRegistry(List.of());
    }

    public List<ParsedTestRun> parse(CoverageInputArtifact artifact, byte[] content) {
        TestResultParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(artifact))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported test result artifact format: "
                                + artifact.normalizedFormat()
                                + " for "
                                + artifact.name()));
        return parser.parse(artifact, content);
    }
}
