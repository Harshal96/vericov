package dev.vericov.analysis.coverage;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.w3c.dom.Element;

public class CloverCoverageParser implements CoverageParser {
    private final SecureXmlCoverageDocumentReader xmlReader;

    public CloverCoverageParser(SecureXmlCoverageDocumentReader xmlReader) {
        this.xmlReader = Objects.requireNonNull(xmlReader, "xmlReader");
    }

    @Override
    public boolean supports(CoverageInputArtifact artifact) {
        String format = artifact.normalizedFormat();
        String name = artifact.normalizedName();
        return "clover".equals(format)
                || "clover-xml".equals(format)
                || name.endsWith("clover.xml");
    }

    @Override
    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        Element root = xmlReader.read(artifact.name(), content).getDocumentElement();
        List<ParsedCoverageFile> files = XmlCoverageElements.descendants(root, "file").stream()
                .map(CloverCoverageParser::parseFile)
                .filter(file -> !file.filePath().isBlank())
                .sorted(Comparator.comparing(ParsedCoverageFile::filePath))
                .toList();

        if (files.isEmpty()) {
            throw new IllegalStateException("No Clover file records found in " + artifact.name());
        }

        return new ParsedCoverage(files);
    }

    private static ParsedCoverageFile parseFile(Element fileElement) {
        String filePath = CoveragePath.normalize(XmlCoverageElements.attr(fileElement, "path"));
        if (filePath.isBlank()) {
            filePath = CoveragePath.normalize(XmlCoverageElements.attr(fileElement, "name"));
        }
        FileAccumulator file = new FileAccumulator(filePath);
        for (Element lineElement : XmlCoverageElements.children(fileElement, "line")) {
            parseLine(lineElement, file);
        }
        return file.toParsedFile();
    }

    private static void parseLine(Element lineElement, FileAccumulator file) {
        int lineNumber = XmlCoverageElements.intAttr(lineElement, "num");
        if (lineNumber <= 0) {
            return;
        }
        int count = XmlCoverageElements.intAttr(lineElement, "count");
        file.addLine(lineNumber, count > 0);

        String type = XmlCoverageElements.attr(lineElement, "type");
        if ("method".equals(type)) {
            addFunction(lineElement, file, lineNumber, count);
        }
        if ("cond".equals(type)) {
            addConditionBranches(lineElement, file, lineNumber);
        }
    }

    private static void addFunction(Element lineElement, FileAccumulator file, int lineNumber, int count) {
        String functionName = XmlCoverageElements.attr(lineElement, "name");
        if (functionName.isBlank()) {
            return;
        }
        String functionKey = functionName + "@" + lineNumber;
        file.functions.add(functionKey);
        if (count > 0) {
            file.coveredFunctions.add(functionKey);
        }
    }

    private static void addConditionBranches(Element lineElement, FileAccumulator file, int lineNumber) {
        String trueBranch = lineNumber + ":true";
        String falseBranch = lineNumber + ":false";
        file.branches.add(trueBranch);
        file.branches.add(falseBranch);
        if (XmlCoverageElements.intAttr(lineElement, "truecount") > 0) {
            file.coveredBranches.add(trueBranch);
        }
        if (XmlCoverageElements.intAttr(lineElement, "falsecount") > 0) {
            file.coveredBranches.add(falseBranch);
        }
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
