package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LcovCoverageParser implements CoverageParser {

    @Override
    public boolean supports(CoverageInputArtifact artifact) {
        String format = artifact.normalizedFormat();
        String name = artifact.normalizedName();
        return "lcov".equals(format) || name.endsWith(".lcov") || name.endsWith(".info");
    }

    @Override
    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        return parse(artifact.name(), content);
    }

    public ParsedCoverage parse(String artifactName, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        Map<String, FileAccumulator> files = new HashMap<>();
        FileAccumulator current = null;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("TN:")) {
                continue;
            }
            if (line.startsWith("SF:")) {
                String filePath = CoveragePath.normalize(line.substring("SF:".length()));
                current = files.computeIfAbsent(filePath, FileAccumulator::new);
                continue;
            }
            if ("end_of_record".equals(line)) {
                current = null;
                continue;
            }
            if (current == null) {
                continue;
            }
            parseCoverageLine(current, line);
        }

        if (files.isEmpty()) {
            throw new IllegalStateException("No LCOV file records found in " + artifactName);
        }

        List<ParsedCoverageFile> parsedFiles = files.values().stream()
                .map(FileAccumulator::toParsedFile)
                .sorted(Comparator.comparing(ParsedCoverageFile::filePath))
                .toList();
        return new ParsedCoverage(parsedFiles);
    }

    private static void parseCoverageLine(FileAccumulator current, String line) {
        if (line.startsWith("DA:")) {
            parseLineCoverage(current, line.substring("DA:".length()));
            return;
        }
        if (line.startsWith("BRDA:")) {
            parseBranchCoverage(current, line.substring("BRDA:".length()));
            return;
        }
        if (line.startsWith("FN:")) {
            parseFunctionDefinition(current, line.substring("FN:".length()));
            return;
        }
        if (line.startsWith("FNDA:")) {
            parseFunctionCoverage(current, line.substring("FNDA:".length()));
        }
    }

    private static void parseLineCoverage(FileAccumulator current, String value) {
        String[] parts = value.split(",");
        int lineNumber = Integer.parseInt(parts[0]);
        long hits = Long.parseLong(parts[1]);
        current.lineHits.merge(lineNumber, hits, Long::sum);
        String statementKey = Integer.toString(lineNumber);
        current.statements.add(statementKey);
        if (hits > 0) {
            current.coveredStatements.add(statementKey);
        }
    }

    private static void parseBranchCoverage(FileAccumulator current, String value) {
        String[] parts = value.split(",");
        String branchKey = parts[0] + ":" + parts[1] + ":" + parts[2];
        current.branches.add(branchKey);
        if (!"-".equals(parts[3]) && Long.parseLong(parts[3]) > 0) {
            current.coveredBranches.add(branchKey);
        }
    }

    private static void parseFunctionDefinition(FileAccumulator current, String value) {
        int comma = value.indexOf(',');
        if (comma >= 0 && comma + 1 < value.length()) {
            current.functions.add(value.substring(comma + 1));
        }
    }

    private static void parseFunctionCoverage(FileAccumulator current, String value) {
        int comma = value.indexOf(',');
        if (comma < 0 || comma + 1 >= value.length()) {
            return;
        }
        long hits = Long.parseLong(value.substring(0, comma));
        String functionName = value.substring(comma + 1);
        current.functions.add(functionName);
        if (hits > 0) {
            current.coveredFunctions.add(functionName);
        }
    }

    private static final class FileAccumulator {
        private final String filePath;
        private final Map<Integer, Long> lineHits = new HashMap<>();
        private final Set<String> branches = new HashSet<>();
        private final Set<String> coveredBranches = new HashSet<>();
        private final Set<String> functions = new HashSet<>();
        private final Set<String> coveredFunctions = new HashSet<>();
        private final Set<String> statements = new HashSet<>();
        private final Set<String> coveredStatements = new HashSet<>();

        private FileAccumulator(String filePath) {
            this.filePath = filePath;
        }

        private ParsedCoverageFile toParsedFile() {
            return new ParsedCoverageFile(
                    filePath,
                    lineHits,
                    branches,
                    coveredBranches,
                    functions,
                    coveredFunctions,
                    statements,
                    coveredStatements);
        }
    }
}
