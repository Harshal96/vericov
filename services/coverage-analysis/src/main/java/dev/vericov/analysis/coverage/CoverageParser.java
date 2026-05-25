package dev.vericov.analysis.coverage;

public interface CoverageParser {
    boolean supports(CoverageInputArtifact artifact);

    ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content);
}
