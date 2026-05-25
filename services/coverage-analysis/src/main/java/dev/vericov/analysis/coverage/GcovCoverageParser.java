package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GcovCoverageParser implements CoverageParser {
    private static final Pattern BRANCH = Pattern.compile("^branch\\s+(\\d+)\\s+(.+)$");
    private static final Pattern FUNCTION = Pattern.compile("^function\\s+(.+)\\s+called\\s+(\\d+)\\b.*$");
    private static final Pattern TAKEN_PERCENT = Pattern.compile(".*taken\\s+(\\d+)%.*");

    @Override
    public boolean supports(CoverageInputArtifact artifact) {
        String format = artifact.normalizedFormat();
        String name = artifact.normalizedName();
        return "gcov".equals(format)
                || "llvm-cov".equals(format)
                || "llvm-cov-gcov".equals(format)
                || name.endsWith(".gcov");
    }

    @Override
    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        FileAccumulator file = new FileAccumulator(fallbackFilePath(artifact));
        int currentLineNumber = 0;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("branch ")) {
                parseBranch(line, file, currentLineNumber);
                continue;
            }
            if (line.startsWith("function ")) {
                parseFunction(line, file);
                continue;
            }
            Integer parsedLineNumber = parseSourceLine(line, file);
            if (parsedLineNumber != null && parsedLineNumber > 0) {
                currentLineNumber = parsedLineNumber;
            }
        }

        if (file.executableLines().isEmpty()) {
            throw new IllegalStateException("No gcov coverage records found in " + artifact.name());
        }

        return new ParsedCoverage(List.of(file.toParsedFile()));
    }

    private static Integer parseSourceLine(String line, FileAccumulator file) {
        int firstColon = line.indexOf(':');
        if (firstColon < 0) {
            return null;
        }
        int secondColon = line.indexOf(':', firstColon + 1);
        if (secondColon < 0) {
            return null;
        }

        String count = line.substring(0, firstColon).trim();
        int lineNumber = Integer.parseInt(line.substring(firstColon + 1, secondColon).trim());
        String code = line.substring(secondColon + 1).trim();
        if (lineNumber == 0) {
            if (code.startsWith("Source:")) {
                file.setFilePath(CoveragePath.normalize(code.substring("Source:".length())));
            }
            return null;
        }
        if ("-".equals(count) || count.isBlank()) {
            return null;
        }
        boolean covered = !("#####".equals(count) || "=====".equals(count)) && Long.parseLong(count) > 0;
        file.addLine(lineNumber, covered);
        return lineNumber;
    }

    private static void parseBranch(String line, FileAccumulator file, int currentLineNumber) {
        if (currentLineNumber <= 0) {
            return;
        }
        Matcher matcher = BRANCH.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        String branchKey = currentLineNumber + ":" + matcher.group(1);
        file.branches().add(branchKey);
        Matcher takenMatcher = TAKEN_PERCENT.matcher(matcher.group(2));
        if (takenMatcher.matches() && Integer.parseInt(takenMatcher.group(1)) > 0) {
            file.coveredBranches().add(branchKey);
        }
    }

    private static void parseFunction(String line, FileAccumulator file) {
        Matcher matcher = FUNCTION.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        String functionKey = matcher.group(1).trim();
        file.functions().add(functionKey);
        if (Long.parseLong(matcher.group(2)) > 0) {
            file.coveredFunctions().add(functionKey);
        }
    }

    private static String fallbackFilePath(CoverageInputArtifact artifact) {
        String name = artifact.name() == null ? "" : artifact.name();
        return name.endsWith(".gcov") ? name.substring(0, name.length() - ".gcov".length()) : name;
    }

    private static final class FileAccumulator {
        private String filePath;
        private final Set<Integer> executableLines = new HashSet<>();
        private final Set<Integer> coveredLines = new HashSet<>();
        private final Set<String> branches = new HashSet<>();
        private final Set<String> coveredBranches = new HashSet<>();
        private final Set<String> functions = new HashSet<>();
        private final Set<String> coveredFunctions = new HashSet<>();
        private final Set<String> statements = new HashSet<>();
        private final Set<String> coveredStatements = new HashSet<>();

        private FileAccumulator(String filePath) {
            this.filePath = filePath;
        }

        private Set<Integer> executableLines() {
            return executableLines;
        }

        private Set<String> branches() {
            return branches;
        }

        private Set<String> coveredBranches() {
            return coveredBranches;
        }

        private Set<String> functions() {
            return functions;
        }

        private Set<String> coveredFunctions() {
            return coveredFunctions;
        }

        private void setFilePath(String filePath) {
            if (!filePath.isBlank()) {
                this.filePath = filePath;
            }
        }

        private void addLine(int lineNumber, boolean covered) {
            String statementKey = Integer.toString(lineNumber);
            executableLines.add(lineNumber);
            statements.add(statementKey);
            if (covered) {
                coveredLines.add(lineNumber);
                coveredStatements.add(statementKey);
            }
        }

        private ParsedCoverageFile toParsedFile() {
            return new ParsedCoverageFile(
                    filePath,
                    executableLines,
                    coveredLines,
                    branches,
                    coveredBranches,
                    functions,
                    coveredFunctions,
                    statements,
                    coveredStatements);
        }
    }
}
