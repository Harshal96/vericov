package dev.vericov.analysis.coverage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.w3c.dom.Element;

public class JacocoCoverageParser implements CoverageParser {
    private final SecureXmlCoverageDocumentReader xmlReader;

    public JacocoCoverageParser(SecureXmlCoverageDocumentReader xmlReader) {
        this.xmlReader = Objects.requireNonNull(xmlReader, "xmlReader");
    }

    @Override
    public boolean supports(CoverageInputArtifact artifact) {
        String format = artifact.normalizedFormat();
        String name = artifact.normalizedName();
        return "jacoco".equals(format)
                || "jacoco-xml".equals(format)
                || name.endsWith("jacoco.xml");
    }

    @Override
    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        Element root = xmlReader.read(artifact.name(), content).getDocumentElement();
        Map<String, FileAccumulator> files = new HashMap<>();

        for (Element packageElement : XmlCoverageElements.descendants(root, "package")) {
            String packageName = CoveragePath.normalize(XmlCoverageElements.attr(packageElement, "name"));
            parseSourceFiles(packageElement, packageName, files);
            parseClassMethods(packageElement, packageName, files);
        }

        if (files.isEmpty()) {
            throw new IllegalStateException("No JaCoCo file records found in " + artifact.name());
        }

        List<ParsedCoverageFile> parsedFiles = files.values().stream()
                .map(FileAccumulator::toParsedFile)
                .sorted(Comparator.comparing(ParsedCoverageFile::filePath))
                .toList();
        return new ParsedCoverage(parsedFiles);
    }

    private static void parseSourceFiles(
            Element packageElement,
            String packageName,
            Map<String, FileAccumulator> files) {
        for (Element sourceFileElement : XmlCoverageElements.children(packageElement, "sourcefile")) {
            String filePath = sourceFilePath(packageName, XmlCoverageElements.attr(sourceFileElement, "name"));
            FileAccumulator file = files.computeIfAbsent(filePath, FileAccumulator::new);
            for (Element lineElement : XmlCoverageElements.children(sourceFileElement, "line")) {
                int lineNumber = XmlCoverageElements.intAttr(lineElement, "nr");
                int missedInstructions = XmlCoverageElements.intAttr(lineElement, "mi");
                int coveredInstructions = XmlCoverageElements.intAttr(lineElement, "ci");
                if (lineNumber <= 0 || missedInstructions + coveredInstructions == 0) {
                    continue;
                }
                file.addLine(lineNumber, coveredInstructions > 0);
                addBranches(
                        file,
                        lineNumber,
                        XmlCoverageElements.intAttr(lineElement, "mb"),
                        XmlCoverageElements.intAttr(lineElement, "cb"));
            }
        }
    }

    private static void parseClassMethods(
            Element packageElement,
            String packageName,
            Map<String, FileAccumulator> files) {
        for (Element classElement : XmlCoverageElements.children(packageElement, "class")) {
            String filePath = sourceFilePath(packageName, XmlCoverageElements.attr(classElement, "sourcefilename"));
            if (filePath.isBlank()) {
                continue;
            }
            FileAccumulator file = files.computeIfAbsent(filePath, FileAccumulator::new);
            String className = XmlCoverageElements.attr(classElement, "name");
            for (Element methodElement : XmlCoverageElements.children(classElement, "method")) {
                parseMethod(file, className, methodElement);
            }
        }
    }

    private static void parseMethod(FileAccumulator file, String className, Element methodElement) {
        String methodName = XmlCoverageElements.attr(methodElement, "name");
        if (methodName.isBlank()) {
            return;
        }
        String functionKey = className
                + "#"
                + methodName
                + XmlCoverageElements.attr(methodElement, "desc")
                + "@"
                + XmlCoverageElements.attr(methodElement, "line");
        file.functions.add(functionKey);
        if (methodCovered(methodElement)) {
            file.coveredFunctions.add(functionKey);
        }
    }

    private static boolean methodCovered(Element methodElement) {
        for (Element counterElement : XmlCoverageElements.children(methodElement, "counter")) {
            if ("METHOD".equals(XmlCoverageElements.attr(counterElement, "type"))) {
                return XmlCoverageElements.intAttr(counterElement, "covered") > 0;
            }
        }
        return false;
    }

    private static void addBranches(FileAccumulator file, int lineNumber, int missedBranches, int coveredBranches) {
        int totalBranches = missedBranches + coveredBranches;
        for (int index = 0; index < totalBranches; index++) {
            String branchKey = lineNumber + ":" + index;
            file.branches.add(branchKey);
            if (index < coveredBranches) {
                file.coveredBranches.add(branchKey);
            }
        }
    }

    private static String sourceFilePath(String packageName, String sourceFileName) {
        if (sourceFileName.isBlank()) {
            return "";
        }
        if (packageName.isBlank()) {
            return CoveragePath.normalize(sourceFileName);
        }
        return CoveragePath.normalize(packageName + "/" + sourceFileName);
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
