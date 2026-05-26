package dev.vericov.analysis.testresults;

import dev.vericov.analysis.coverage.CoverageInputArtifact;
import java.util.List;

public interface TestResultParser {
    boolean supports(CoverageInputArtifact artifact);

    List<ParsedTestRun> parse(CoverageInputArtifact artifact, byte[] content);
}
