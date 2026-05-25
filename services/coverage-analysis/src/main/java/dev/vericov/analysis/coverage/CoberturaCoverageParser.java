package dev.vericov.analysis.coverage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

public class CoberturaCoverageParser implements CoverageParser {
    private static final Pattern CONDITION_COVERAGE = Pattern.compile(".*\\((\\d+)/(\\d+)\\).*");

    private final SecureXmlCoverageDocumentReader xmlReader;

    public CoberturaCoverageParser(SecureXmlCoverageDocumentReader xmlReader) {
        this.xmlReader = Objects.requireNonNull(xmlReader, "xmlReader");
    }

    @Override
    public boolean supports(CoverageInputArtifact artifact) {
        String format = artifact.normalizedFormat();
        String name = artifact.normalizedName();
        return "cobertura".equals(format)
                || "cobertura-xml".equals(format)
                || "coverage.py-xml".equals(format)
                || "coveragepy-xml".equals(format)
                || name.endsWith("cobertura.xml");
    }

    @Override
    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        Element root = xmlReader.read(artifact.name(), content).getDocumentElement();
        Map<String, FileAccumulator> files = new HashMap<>();

        for (Element classElement : XmlCoverageElements.descendants(root, "class")) {
            String filePath = CoveragePath.normalize(XmlCoverageElements.attr(classElement, "filename"));
            if (filePath.isBlank()) {
                continue;
            }
            FileAccumulator file = files.computeIfAbsent(filePath, FileAccumulator::new);
            parseClassLines(classElement, file);
            parseMethods(classElement, file);
        }

        if (files.isEmpty()) {
            throw new IllegalStateException("No Cobertura file records found in " + artifact.name());
        }

        List<ParsedCoverageFile> parsedFiles = files.values().stream()
                .map(FileAccumulator::toParsedFile)
                .sorted(Comparator.comparing(ParsedCoverageFile::filePath))
                .toList();
        return new ParsedCoverage(parsedFiles);
    }

    private static void parseClassLines(Element classElement, FileAccumulator file) {
        for (Element linesElement : XmlCoverageElements.children(classElement, "lines")) {
            for (Element lineElement : XmlCoverageElements.children(linesElement, "line")) {
                int lineNumber = XmlCoverageElements.intAttr(lineElement, "number");
                if (lineNumber <= 0) {
                    continue;
                }
                int hits = XmlCoverageElements.intAttr(lineElement, "hits");
                file.addLine(lineNumber, hits > 0);
                parseBranchCoverage(lineElement, file, lineNumber);
            }
        }
    }

    private static void parseBranchCoverage(Element lineElement, FileAccumulator file, int lineNumber) {
        if (!"true".equalsIgnoreCase(XmlCoverageElements.attr(lineElement, "branch"))) {
            return;
        }
        Matcher matcher = CONDITION_COVERAGE.matcher(XmlCoverageElements.attr(lineElement, "condition-coverage"));
        if (!matcher.matches()) {
            return;
        }
        int covered = Integer.parseInt(matcher.group(1));
        int total = Integer.parseInt(matcher.group(2));
        for (int index = 0; index < total; index++) {
            String branchKey = lineNumber + ":" + index;
            file.branches.add(branchKey);
            if (index < covered) {
                file.coveredBranches.add(branchKey);
            }
        }
    }

    private static void parseMethods(Element classElement, FileAccumulator file) {
        for (Element methodsElement : XmlCoverageElements.children(classElement, "methods")) {
            for (Element methodElement : XmlCoverageElements.children(methodsElement, "method")) {
                parseMethod(methodElement, file);
            }
        }
    }

    private static void parseMethod(Element methodElement, FileAccumulator file) {
        String methodName = XmlCoverageElements.attr(methodElement, "name");
        if (methodName.isBlank()) {
            return;
        }

        int firstLineNumber = 0;
        boolean coveredByLine = false;
        for (Element linesElement : XmlCoverageElements.children(methodElement, "lines")) {
            for (Element lineElement : XmlCoverageElements.children(linesElement, "line")) {
                int lineNumber = XmlCoverageElements.intAttr(lineElement, "number");
                if (lineNumber > 0 && (firstLineNumber == 0 || lineNumber < firstLineNumber)) {
                    firstLineNumber = lineNumber;
                }
                coveredByLine = coveredByLine || XmlCoverageElements.intAttr(lineElement, "hits") > 0;
            }
        }

        String functionKey = methodName
                + XmlCoverageElements.attr(methodElement, "signature")
                + "@"
                + firstLineNumber;
        file.functions.add(functionKey);
        if (coveredByLine || parseRate(XmlCoverageElements.attr(methodElement, "line-rate")) > 0.0) {
            file.coveredFunctions.add(functionKey);
        }
    }

    private static double parseRate(String value) {
        return value.isBlank() ? 0.0 : Double.parseDouble(value);
    }

    private static final class FileAccumulator {
        private final String filePath;
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
