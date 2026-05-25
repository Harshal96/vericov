package dev.vericov.analysis.coverage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ParsedCoverageFile(
        String filePath,
        Map<Integer, Long> lineHits,
        Set<String> branches,
        Set<String> coveredBranches,
        Set<String> functions,
        Set<String> coveredFunctions,
        Set<String> statements,
        Set<String> coveredStatements) {

    public ParsedCoverageFile {
        Objects.requireNonNull(filePath, "filePath");
        lineHits = Map.copyOf(lineHits == null ? Map.of() : lineHits);
        branches = Set.copyOf(branches == null ? Set.of() : branches);
        coveredBranches = Set.copyOf(coveredBranches == null ? Set.of() : coveredBranches);
        functions = Set.copyOf(functions == null ? Set.of() : functions);
        coveredFunctions = Set.copyOf(coveredFunctions == null ? Set.of() : coveredFunctions);
        statements = Set.copyOf(statements == null ? Set.of() : statements);
        coveredStatements = Set.copyOf(coveredStatements == null ? Set.of() : coveredStatements);
    }

    public ParsedCoverageFile(
            String filePath,
            Map<Integer, Long> lineHits,
            Set<String> branches,
            Set<String> coveredBranches,
            Set<String> functions,
            Set<String> coveredFunctions) {
        this(
                filePath,
                lineHits,
                branches,
                coveredBranches,
                functions,
                coveredFunctions,
                lineHits == null
                        ? Set.of()
                        : lineHits.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet()),
                lineHits == null
                        ? Set.of()
                        : lineHits.entrySet().stream()
                                .filter(entry -> entry.getValue() > 0)
                                .map(entry -> String.valueOf(entry.getKey()))
                                .collect(Collectors.toUnmodifiableSet()));
    }

    public ParsedCoverageFile(
            String filePath,
            Set<Integer> executableLines,
            Set<Integer> coveredLines,
            Set<String> branches,
            Set<String> coveredBranches,
            Set<String> functions,
            Set<String> coveredFunctions,
            Set<String> statements,
            Set<String> coveredStatements) {
        this(
                filePath,
                lineHitsFrom(executableLines, coveredLines),
                branches,
                coveredBranches,
                functions,
                coveredFunctions,
                statements,
                coveredStatements);
    }

    public Set<Integer> executableLines() {
        return lineHits.keySet();
    }

    public Set<Integer> coveredLines() {
        return lineHits.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public CoverageMetric line() {
        return new CoverageMetric(coveredLines().size(), executableLines().size());
    }

    public CoverageMetric branch() {
        return new CoverageMetric(coveredBranches.size(), branches.size());
    }

    public CoverageMetric function() {
        return new CoverageMetric(coveredFunctions.size(), functions.size());
    }

    public CoverageMetric statement() {
        return new CoverageMetric(coveredStatements.size(), statements.size());
    }

    private static Map<Integer, Long> lineHitsFrom(Set<Integer> executableLines, Set<Integer> coveredLines) {
        Set<Integer> safeExecutableLines = executableLines == null ? Set.of() : executableLines;
        Set<Integer> safeCoveredLines = coveredLines == null ? Set.of() : coveredLines;
        Map<Integer, Long> hits = new HashMap<>();
        for (Integer line : safeExecutableLines) {
            hits.put(line, safeCoveredLines.contains(line) ? 1L : 0L);
        }
        for (Integer line : safeCoveredLines) {
            hits.putIfAbsent(line, 1L);
        }
        return hits;
    }
}
