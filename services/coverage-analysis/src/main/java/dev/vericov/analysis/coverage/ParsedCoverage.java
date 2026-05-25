package dev.vericov.analysis.coverage;

import java.util.List;

public record ParsedCoverage(List<ParsedCoverageFile> files) {

    public ParsedCoverage {
        files = List.copyOf(files == null ? List.of() : files);
    }
}
