package dev.vericov.analysis.coverage;

import java.util.List;
import java.util.Objects;

public class CoverageParserRegistry {
    private final List<CoverageParser> parsers;

    public CoverageParserRegistry(List<CoverageParser> parsers) {
        this.parsers = List.copyOf(Objects.requireNonNull(parsers, "parsers"));
    }

    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        CoverageParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(artifact))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported coverage artifact format: "
                                + artifact.normalizedFormat()
                                + " for "
                                + artifact.name()));
        return parser.parse(artifact, content);
    }
}
