package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoCoverProfileParser implements CoverageParser {
    private static final Pattern COVERAGE_BLOCK =
            Pattern.compile("^(.+):(\\d+)\\.\\d+,(\\d+)\\.\\d+\\s+(\\d+)\\s+(\\d+)$");

    @Override
    public boolean supports(CoverageInputArtifact artifact) {
        String format = artifact.normalizedFormat();
        String name = artifact.normalizedName();
        return "go".equals(format)
                || "go-cover".equals(format)
                || "go-coverprofile".equals(format)
                || "gocover".equals(format)
                || name.endsWith(".coverprofile")
                || name.endsWith("cover.out");
    }

    @Override
    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        Map<String, FileAccumulator> files = new HashMap<>();

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("mode:")) {
                continue;
            }
            Matcher matcher = COVERAGE_BLOCK.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String filePath = CoveragePath.normalize(matcher.group(1));
            int startLine = Integer.parseInt(matcher.group(2));
            int endLine = Integer.parseInt(matcher.group(3));
            int statementCount = Integer.parseInt(matcher.group(4));
            long hits = Long.parseLong(matcher.group(5));
            files.computeIfAbsent(filePath, FileAccumulator::new)
                    .addBlock(startLine, endLine, statementCount, hits > 0);
        }

        if (files.isEmpty()) {
            throw new IllegalStateException("No Go coverage records found in " + artifact.name());
        }

        List<ParsedCoverageFile> parsedFiles = files.values().stream()
                .map(FileAccumulator::toParsedFile)
                .sorted(Comparator.comparing(ParsedCoverageFile::filePath))
                .toList();
        return new ParsedCoverage(parsedFiles);
    }

    private static final class FileAccumulator {
        private final String filePath;
        private final Set<Integer> executableLines = new HashSet<>();
        private final Set<Integer> coveredLines = new HashSet<>();
        private final Set<String> statements = new HashSet<>();
        private final Set<String> coveredStatements = new HashSet<>();

        private FileAccumulator(String filePath) {
            this.filePath = filePath;
        }

        private void addBlock(int startLine, int endLine, int statementCount, boolean covered) {
            for (int lineNumber = startLine; lineNumber <= endLine; lineNumber++) {
                executableLines.add(lineNumber);
                if (covered) {
                    coveredLines.add(lineNumber);
                }
            }
            String blockKey = startLine + "-" + endLine;
            for (int index = 0; index < statementCount; index++) {
                String statementKey = blockKey + ":" + index;
                statements.add(statementKey);
                if (covered) {
                    coveredStatements.add(statementKey);
                }
            }
        }

        private ParsedCoverageFile toParsedFile() {
            return new ParsedCoverageFile(
                    filePath,
                    executableLines,
                    coveredLines,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    statements,
                    coveredStatements);
        }
    }
}
