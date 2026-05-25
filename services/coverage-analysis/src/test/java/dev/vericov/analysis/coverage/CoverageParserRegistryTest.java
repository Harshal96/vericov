package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoverageParserRegistryTest {
    @Test
    void parsesWithFirstSupportingParser() {
        CoverageParser parser = new StubParser("cobertura");
        CoverageParserRegistry registry = new CoverageParserRegistry(List.of(parser));
        CoverageInputArtifact artifact = new CoverageInputArtifact(
                "coverage.xml",
                "coverage",
                "cobertura",
                "bucket",
                "path",
                "sha");

        ParsedCoverage parsed = registry.parse(artifact, "content".getBytes(StandardCharsets.UTF_8));

        assertEquals("src/cobertura.java", parsed.files().getFirst().filePath());
    }

    @Test
    void failsWithArtifactNameAndFormatWhenUnsupported() {
        CoverageParserRegistry registry = new CoverageParserRegistry(List.of(new StubParser("lcov")));
        CoverageInputArtifact artifact = new CoverageInputArtifact(
                "coverage.xml",
                "coverage",
                "jacoco",
                "bucket",
                "path",
                "sha");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> registry.parse(artifact, new byte[0]));

        assertEquals("Unsupported coverage artifact format: jacoco for coverage.xml", exception.getMessage());
    }

    private record StubParser(String supportedFormat) implements CoverageParser {
        @Override
        public boolean supports(CoverageInputArtifact artifact) {
            return supportedFormat.equals(artifact.normalizedFormat());
        }

        @Override
        public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
            ParsedCoverageFile file = new ParsedCoverageFile(
                    "src/" + supportedFormat + ".java",
                    Set.of(1),
                    Set.of(1),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of("1"),
                    Set.of("1"));
            return new ParsedCoverage(List.of(file));
        }
    }
}
