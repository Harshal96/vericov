package dev.vericov.analysis.coverage;

import dev.vericov.ignore.CoverageIgnoreRules;
import java.util.List;
import java.util.Objects;

public final class CoverageFileFilter {
    private final CoverageIgnoreRules rules;

    public CoverageFileFilter(List<String> rules) {
        this.rules = new CoverageIgnoreRules(rules);
    }

    public ParsedCoverage filter(ParsedCoverage coverage) {
        Objects.requireNonNull(coverage, "coverage");
        return new ParsedCoverage(coverage.files().stream()
                .filter(file -> !rules.isIgnored(file.filePath()))
                .toList());
    }
}
