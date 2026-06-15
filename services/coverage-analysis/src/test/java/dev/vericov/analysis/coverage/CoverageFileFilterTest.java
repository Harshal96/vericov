package dev.vericov.analysis.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoverageFileFilterTest {

    @Test
    void filtersFilesWithOrderedNegationWithoutChangingIncludedValues() {
        ParsedCoverageFile excluded = file("vendor/third_party/Lib.java", 0);
        ParsedCoverageFile included = file("vendor/maintained/App.java", 3);
        ParsedCoverage parsed = new ParsedCoverage(List.of(excluded, included));
        CoverageFileFilter filter = new CoverageFileFilter(List.of(
                "vendor/**",
                "!vendor/maintained/**"));

        ParsedCoverage filtered = filter.filter(parsed);

        assertEquals(List.of(included), filtered.files());
    }

    private static ParsedCoverageFile file(String path, long hits) {
        return new ParsedCoverageFile(
                path,
                Map.of(1, hits),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of());
    }
}
