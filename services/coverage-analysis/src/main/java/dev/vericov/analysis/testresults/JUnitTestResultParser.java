package dev.vericov.analysis.testresults;

import dev.vericov.analysis.coverage.CoverageInputArtifact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JUnitTestResultParser implements TestResultParser {
    private final SecureXmlTestResultDocumentReader documentReader;

    public JUnitTestResultParser(SecureXmlTestResultDocumentReader documentReader) {
        this.documentReader = Objects.requireNonNull(documentReader, "documentReader");
    }

    @Override
    public boolean supports(CoverageInputArtifact artifact) {
        return artifact.isTestResultArtifact() && "junit".equals(artifact.normalizedFormat());
    }

    @Override
    public List<ParsedTestRun> parse(CoverageInputArtifact artifact, byte[] content) {
        Element root = documentReader.read(artifact.name(), content).getDocumentElement();
        List<Element> suites = suites(root, artifact);
        List<ParsedTestRun> runs = new ArrayList<>(suites.size());
        for (int index = 0; index < suites.size(); index++) {
            runs.add(parseSuite(artifact, suites.get(index), index));
        }
        return List.copyOf(runs);
    }

    private static List<Element> suites(Element root, CoverageInputArtifact artifact) {
        if ("testsuite".equals(root.getTagName())) {
            return List.of(root);
        }
        if ("testsuites".equals(root.getTagName())) {
            return descendants(root, "testsuite");
        }
        throw new IllegalStateException(
                "Unsupported JUnit XML root element: " + root.getTagName() + " for " + artifact.name());
    }

    private static ParsedTestRun parseSuite(CoverageInputArtifact artifact, Element suite, int suiteIndex) {
        String suiteName = attr(suite, "name");
        if (suiteName.isBlank()) {
            suiteName = artifact.name();
        }

        List<Element> cases = children(suite, "testcase");
        int total = intAttrOrDefault(suite, "tests", cases.size());
        int failed = intAttrOrDefault(suite, "failures", countCasesWithChild(cases, "failure"));
        int errors = intAttrOrDefault(suite, "errors", countCasesWithChild(cases, "error"));
        int skipped = intAttrOrDefault(suite, "skipped", countCasesWithChild(cases, "skipped"));
        int passed = total - failed - errors - skipped;
        if (total < 0 || failed < 0 || errors < 0 || skipped < 0 || passed < 0) {
            throw new IllegalStateException(
                    "Invalid JUnit test counts for suite " + suiteName + " in " + artifact.name());
        }

        return new ParsedTestRun(
                suiteName,
                suiteIndex,
                status(total, failed, errors, skipped),
                total,
                passed,
                failed,
                errors,
                skipped,
                durationMs(suite, cases));
    }

    private static String status(int total, int failed, int errors, int skipped) {
        if (errors > 0) {
            return "error";
        }
        if (failed > 0) {
            return "failed";
        }
        if (total > 0 && skipped == total) {
            return "skipped";
        }
        return "passed";
    }

    private static long durationMs(Element suite, List<Element> cases) {
        String suiteTime = attr(suite, "time");
        if (!suiteTime.isBlank()) {
            return secondsToMillis(suiteTime);
        }
        long total = 0L;
        for (Element testCase : cases) {
            String caseTime = attr(testCase, "time");
            if (!caseTime.isBlank()) {
                total += secondsToMillis(caseTime);
            }
        }
        return total;
    }

    private static long secondsToMillis(String seconds) {
        return new BigDecimal(seconds)
                .multiply(new BigDecimal("1000"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static int countCasesWithChild(List<Element> cases, String tagName) {
        int count = 0;
        for (Element testCase : cases) {
            if (!children(testCase, tagName).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static int intAttrOrDefault(Element element, String name, int fallback) {
        String value = attr(element, name);
        return value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static String attr(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name).trim() : "";
    }

    private static List<Element> descendants(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return List.copyOf(elements);
    }

    private static List<Element> children(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                elements.add(element);
            }
        }
        return List.copyOf(elements);
    }
}
