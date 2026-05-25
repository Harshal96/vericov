# Coverage Parser Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand Coverage Analysis from LCOV-only ingestion to an extensible parser pipeline that supports LCOV, Cobertura XML, JaCoCo XML, Clover XML, Go cover profiles, and gcov/llvm-cov gcov text output.

**Architecture:** Introduce a small `CoverageParser` contract and `CoverageParserRegistry` so `DefaultCoverageAnalysisProcessor` can parse every uploaded coverage artifact through the matching parser instead of calling `input.lcovCoverageArtifacts()`. Keep parser output normalized to `ParsedCoverage` and `ParsedCoverageFile`, extend the canonical model to represent statement coverage separately from line coverage, and use secure XML parsing for XML report families.

**Tech Stack:** Java 25, Helidon 4 MP, JUnit 6/Jupiter, Maven, Java DOM XML APIs with XXE protections, existing coverage-analysis application/port/domain structure.

---

## Current State

- `DefaultCoverageAnalysisProcessor` directly injects `LcovCoverageParser`.
- `DefaultCoverageAnalysisProcessor.process()` only reads `input.lcovCoverageArtifacts()` at `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java:43`.
- `CoverageAnalysisInput.lcovCoverageArtifacts()` filters coverage artifacts down to LCOV by `format`, `.lcov`, or `.info`.
- `ParsedCoverageFile.statement()` mirrors `line()`; this loses useful statement totals for Go profiles and Clover.
- `docs/backend/services/04-coverage-analysis-service.md` documents LCOV-only parser support.
- `docs/prd/vericov-agentic-coverage-platform.md` calls for LCOV, Cobertura XML, JaCoCo XML, Go profiles, gcov/llvm-cov, Clover XML, Istanbul/nyc JSON and LCOV, coverage.py XML/JSON, and generic JSON.

## Completion Definition

- `DefaultCoverageAnalysisProcessor` parses all uploaded artifacts where `kind == "coverage"` through a parser registry.
- Unsupported coverage artifact formats fail with a clear message naming the artifact and format.
- LCOV behavior remains backward-compatible.
- Cobertura XML, JaCoCo XML, Clover XML, Go cover profiles, and gcov/llvm-cov gcov text output have unit tests with line, branch, function, and statement assertions.
- Cobertura-compatible coverage.py XML is accepted by the Cobertura parser.
- XML parsers reject reports containing a `DOCTYPE` instead of resolving external entities.
- BDD coverage-analysis scenarios include a mixed-format upload.
- Coverage Analysis service docs list the newly supported parser families and state the JSON parser families that remain separate work.
- `mvn -pl services/coverage-analysis test` passes.

## Scope Boundary

This plan covers parser ingestion and normalized metric calculation only. It does not add upload API validation for allowed formats, PR diff coverage, gate evaluation, report flags, path remapping against repository checkout metadata, Istanbul/nyc JSON, coverage.py JSON, or the generic JSON adapter.

## File Structure

Create these files:

- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageParser.java`: parser interface used by the processor and registry.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageParserRegistry.java`: parser selection and unsupported-format failure.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoveragePath.java`: small path normalization helper shared by parsers.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/SecureXmlCoverageDocumentReader.java`: DOM reader with external entity and `DOCTYPE` protections.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/XmlCoverageElements.java`: DOM traversal helpers for direct child elements, attributes, counters, and integer parsing.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoberturaCoverageParser.java`: Cobertura XML and coverage.py XML parser.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/JacocoCoverageParser.java`: JaCoCo XML parser.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CloverCoverageParser.java`: Clover XML parser.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/GoCoverProfileParser.java`: Go `coverprofile` parser.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/GcovCoverageParser.java`: gcov/llvm-cov gcov text parser.
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/ParsedCoverageFileTest.java`
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CoverageParserRegistryTest.java`
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/SecureXmlCoverageDocumentReaderTest.java`
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CoberturaCoverageParserTest.java`
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/JacocoCoverageParserTest.java`
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CloverCoverageParserTest.java`
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/GoCoverProfileParserTest.java`
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/GcovCoverageParserTest.java`

Modify these files:

- `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`: replace direct LCOV parser use with parser registry.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`: construct the registry with all parsers.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageAnalysisInput.java`: replace `lcovCoverageArtifacts()` with `coverageArtifacts()`.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageInputArtifact.java`: add normalized format/name helpers for parser support checks.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReportMerger.java`: merge statement coverage sets independently.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/LcovCoverageParser.java`: implement `CoverageParser` and emit statement IDs from line records.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/ParsedCoverageFile.java`: add statement sets.
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/LcovCoverageParserTest.java`: assert independent statement totals match LCOV line totals.
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`: assert mixed-format parser dispatch and unsupported coverage artifact failure.
- `services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java`: construct the processor with a registry and add mixed-format step data.
- `services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature`: add mixed-format upload scenario.
- `docs/backend/services/04-coverage-analysis-service.md`: update parser support section.

---

### Task 1: Add Independent Statement Coverage To The Canonical Model

**Files:**
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/ParsedCoverageFile.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReportMerger.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/LcovCoverageParser.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/LcovCoverageParserTest.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/ParsedCoverageFileTest.java`

- [ ] **Step 1: Write the failing statement model test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/ParsedCoverageFileTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParsedCoverageFileTest {
    @Test
    void statementMetricCanDifferFromLineMetric() {
        ParsedCoverageFile file = new ParsedCoverageFile(
                "src/App.java",
                Set.of(10),
                Set.of(10),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("10:0", "10:1"),
                Set.of("10:0"));

        assertEquals(new CoverageMetric(1, 1), file.line());
        assertEquals(new CoverageMetric(1, 2), file.statement());
    }
}
```

- [ ] **Step 2: Run the failing statement model test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=ParsedCoverageFileTest test
```

Expected: FAIL because `ParsedCoverageFile` does not accept statement sets.

- [ ] **Step 3: Extend `ParsedCoverageFile`**

Change the record to include `Set<String> statements` and `Set<String> coveredStatements`. Keep existing null-to-empty copy behavior for every set, and change `statement()` to:

```java
public CoverageMetric statement() {
    return new CoverageMetric(coveredStatements.size(), statements.size());
}
```

- [ ] **Step 4: Update merger accumulation**

In `CoverageReportMerger.FileAccumulator`, add:

```java
private final Set<String> statements = new HashSet<>();
private final Set<String> coveredStatements = new HashSet<>();
```

Then add these lines to `add(ParsedCoverageFile file)`:

```java
statements.addAll(file.statements());
coveredStatements.addAll(file.coveredStatements());
```

Pass both sets to the `ParsedCoverageFile` constructor in `toParsedFile()`.

- [ ] **Step 5: Update LCOV to emit statement IDs**

In `LcovCoverageParser.FileAccumulator`, add `statements` and `coveredStatements`. In `parseLineCoverage`, after adding the executable line, add:

```java
String statementKey = Integer.toString(lineNumber);
current.statements.add(statementKey);
if (hits > 0) {
    current.coveredStatements.add(statementKey);
}
```

Pass both statement sets to `ParsedCoverageFile`.

- [ ] **Step 6: Update LCOV parser assertions**

In `LcovCoverageParserTest.parsesLineBranchAndFunctionCoverage()`, replace `assertEquals(file.line(), file.statement());` with:

```java
assertEquals(3, file.statement().total());
assertEquals(2, file.statement().covered());
```

- [ ] **Step 7: Run model and LCOV parser tests**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=ParsedCoverageFileTest,LcovCoverageParserTest test
```

Expected: PASS.

---

### Task 2: Introduce Parser Contract And Registry

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageParser.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageParserRegistry.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoveragePath.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageAnalysisInput.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageInputArtifact.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/LcovCoverageParser.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CoverageParserRegistryTest.java`

- [ ] **Step 1: Write failing registry tests**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CoverageParserRegistryTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoverageParserRegistryTest {
    @Test
    void parsesWithFirstSupportingParser() {
        CoverageParser parser = new StubParser("cobertura");
        CoverageParserRegistry registry = new CoverageParserRegistry(List.of(parser));
        CoverageInputArtifact artifact = new CoverageInputArtifact(
                "coverage.xml",
                "coverage",
                "cobertura",
                "bucket",
                "path",
                "sha");

        ParsedCoverage parsed = registry.parse(artifact, "content".getBytes(StandardCharsets.UTF_8));

        assertEquals("src/cobertura.java", parsed.files().getFirst().filePath());
    }

    @Test
    void failsWithArtifactNameAndFormatWhenUnsupported() {
        CoverageParserRegistry registry = new CoverageParserRegistry(List.of(new StubParser("lcov")));
        CoverageInputArtifact artifact = new CoverageInputArtifact(
                "coverage.xml",
                "coverage",
                "jacoco",
                "bucket",
                "path",
                "sha");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> registry.parse(artifact, new byte[0]));

        assertEquals("Unsupported coverage artifact format: jacoco for coverage.xml", exception.getMessage());
    }

    private record StubParser(String supportedFormat) implements CoverageParser {
        @Override
        public boolean supports(CoverageInputArtifact artifact) {
            return supportedFormat.equals(artifact.normalizedFormat());
        }

        @Override
        public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
            ParsedCoverageFile file = new ParsedCoverageFile(
                    "src/" + supportedFormat + ".java",
                    Set.of(1),
                    Set.of(1),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of("1"),
                    Set.of("1"));
            return new ParsedCoverage(List.of(file));
        }
    }
}
```

- [ ] **Step 2: Run the failing registry tests**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=CoverageParserRegistryTest test
```

Expected: FAIL because the parser contract and registry do not exist.

- [ ] **Step 3: Create `CoverageParser`**

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageParser.java`:

```java
package dev.vericov.analysis.coverage;

public interface CoverageParser {
    boolean supports(CoverageInputArtifact artifact);

    ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content);
}
```

- [ ] **Step 4: Create `CoverageParserRegistry`**

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageParserRegistry.java`:

```java
package dev.vericov.analysis.coverage;

import java.util.List;
import java.util.Objects;

public class CoverageParserRegistry {
    private final List<CoverageParser> parsers;

    public CoverageParserRegistry(List<CoverageParser> parsers) {
        this.parsers = List.copyOf(Objects.requireNonNull(parsers, "parsers"));
    }

    public ParsedCoverage parse(CoverageInputArtifact artifact, byte[] content) {
        CoverageParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(artifact))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported coverage artifact format: "
                                + artifact.normalizedFormat()
                                + " for "
                                + artifact.name()));
        return parser.parse(artifact, content);
    }
}
```

- [ ] **Step 5: Add artifact helpers**

In `CoverageInputArtifact`, add:

```java
public String normalizedFormat() {
    return format == null ? "" : format.trim().toLowerCase(java.util.Locale.ROOT);
}

public String normalizedName() {
    return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
}
```

Keep `isCoverageArtifact()`.

- [ ] **Step 6: Replace LCOV-specific input filtering**

In `CoverageAnalysisInput`, replace `lcovCoverageArtifacts()` with:

```java
public List<CoverageInputArtifact> coverageArtifacts() {
    return artifacts.stream()
            .filter(CoverageInputArtifact::isCoverageArtifact)
            .toList();
}
```

- [ ] **Step 7: Add shared path normalization**

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoveragePath.java`:

```java
package dev.vericov.analysis.coverage;

public final class CoveragePath {
    private CoveragePath() {
    }

    public static String normalize(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }
}
```

- [ ] **Step 8: Convert LCOV parser to the interface**

In `LcovCoverageParser`, change the class declaration to:

```java
public class LcovCoverageParser implements CoverageParser {
```

Add:

```java
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
```

Keep the existing `parse(String artifactName, byte[] content)` method for existing tests.

- [ ] **Step 9: Run registry and LCOV tests**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=CoverageParserRegistryTest,LcovCoverageParserTest test
```

Expected: PASS.

---

### Task 3: Wire The Registry Into The Processor

**Files:**
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java`

- [ ] **Step 1: Update processor tests for registry dispatch**

In `DefaultCoverageAnalysisProcessorTest`, replace direct `new LcovCoverageParser()` construction with a helper:

```java
private static CoverageParserRegistry parserRegistry() {
    return new CoverageParserRegistry(List.of(new LcovCoverageParser()));
}
```

Add this test:

```java
@Test
void failsWhenCoverageArtifactFormatIsUnsupported() {
    DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
            new FakeInputRepository(new CoverageAnalysisInput(
                    UPLOAD_ID,
                    TENANT_ID,
                    REPOSITORY_ID,
                    "abc123",
                    "main",
                    null,
                    List.of(new CoverageInputArtifact(
                            "coverage.xml",
                            "coverage",
                            "jacoco",
                            "coverage-raw",
                            "tenant/upload/coverage/coverage.xml",
                            "sha-1")))),
            new FakeContentStore(Map.of("coverage-raw/tenant/upload/coverage/coverage.xml", new byte[0])),
            new FakeReportRepository(),
            parserRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> processor.process(event()));

    assertEquals("Unsupported coverage artifact format: jacoco for coverage.xml", exception.getMessage());
}
```

- [ ] **Step 2: Run the failing processor test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=DefaultCoverageAnalysisProcessorTest test
```

Expected: FAIL because the processor still accepts `LcovCoverageParser`.

- [ ] **Step 3: Change processor dependency to registry**

In `DefaultCoverageAnalysisProcessor`, replace:

```java
private final LcovCoverageParser lcovParser;
```

with:

```java
private final CoverageParserRegistry parserRegistry;
```

Update the constructor parameter and null check.

- [ ] **Step 4: Parse every coverage artifact through the registry**

In `process`, replace:

```java
List<CoverageInputArtifact> coverageArtifacts = input.lcovCoverageArtifacts();
```

with:

```java
List<CoverageInputArtifact> coverageArtifacts = input.coverageArtifacts();
```

Replace parser invocation with:

```java
parsedCoverages.add(parserRegistry.parse(artifact, content));
```

Keep the empty-coverage-artifact message unchanged:

```java
"No coverage artifacts found for upload " + event.uploadId()
```

- [ ] **Step 5: Update CDI component construction**

In `AnalysisComponents`, replace the direct LCOV parser construction with:

```java
new CoverageParserRegistry(List.of(new LcovCoverageParser()))
```

Import `CoverageParserRegistry`.

- [ ] **Step 6: Update BDD step construction**

In `AnalysisSteps.processor()`, pass:

```java
new CoverageParserRegistry(List.of(new LcovCoverageParser()))
```

- [ ] **Step 7: Run processor and BDD tests**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=DefaultCoverageAnalysisProcessorTest,RunAnalysisFeaturesTest test
```

Expected: PASS.

---

### Task 4: Add Secure XML Parser Foundation

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/SecureXmlCoverageDocumentReader.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/XmlCoverageElements.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/SecureXmlCoverageDocumentReaderTest.java`

- [ ] **Step 1: Write failing XML security test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/SecureXmlCoverageDocumentReaderTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureXmlCoverageDocumentReaderTest {
    @Test
    void parsesOrdinaryCoverageXml() {
        SecureXmlCoverageDocumentReader reader = new SecureXmlCoverageDocumentReader();

        String root = reader.read(
                        "coverage.xml",
                        "<coverage><packages /></coverage>".getBytes(StandardCharsets.UTF_8))
                .getDocumentElement()
                .getTagName();

        assertEquals("coverage", root);
    }

    @Test
    void rejectsDoctypeDeclarations() {
        SecureXmlCoverageDocumentReader reader = new SecureXmlCoverageDocumentReader();
        byte[] malicious = """
                <!DOCTYPE coverage [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <coverage>&xxe;</coverage>
                """.getBytes(StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reader.read("coverage.xml", malicious));

        assertEquals("Invalid XML coverage artifact coverage.xml", exception.getMessage());
    }
}
```

- [ ] **Step 2: Run the failing XML security test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=SecureXmlCoverageDocumentReaderTest test
```

Expected: FAIL because the XML reader does not exist.

- [ ] **Step 3: Create secure XML reader**

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/SecureXmlCoverageDocumentReader.java`:

```java
package dev.vericov.analysis.coverage;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

public class SecureXmlCoverageDocumentReader {
    public Document read(String artifactName, byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid XML coverage artifact " + artifactName, exception);
        }
    }
}
```

- [ ] **Step 4: Create DOM helper**

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/XmlCoverageElements.java`:

```java
package dev.vericov.analysis.coverage;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class XmlCoverageElements {
    private XmlCoverageElements() {
    }

    public static List<Element> descendants(Element parent, String tagName) {
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

    public static List<Element> children(Element parent, String tagName) {
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

    public static String attr(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name).trim() : "";
    }

    public static int intAttr(Element element, String name) {
        String value = attr(element, name);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }
}
```

- [ ] **Step 5: Run XML foundation tests**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=SecureXmlCoverageDocumentReaderTest test
```

Expected: PASS.

---

### Task 5: Add Cobertura XML Parser

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoberturaCoverageParser.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CoberturaCoverageParserTest.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`

- [ ] **Step 1: Write failing Cobertura parser test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CoberturaCoverageParserTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoberturaCoverageParserTest {
    @Test
    void parsesLineBranchFunctionAndStatementCoverage() {
        CoberturaCoverageParser parser = new CoberturaCoverageParser(new SecureXmlCoverageDocumentReader());
        byte[] content = """
                <coverage>
                  <packages>
                    <package name="app">
                      <classes>
                        <class name="App" filename="./src/App.py">
                          <methods>
                            <method name="hit" signature="()" line-rate="1.0">
                              <lines><line number="10" hits="1" /></lines>
                            </method>
                            <method name="miss" signature="()" line-rate="0.0">
                              <lines><line number="20" hits="0" /></lines>
                            </method>
                          </methods>
                          <lines>
                            <line number="10" hits="1" branch="true" condition-coverage="50% (1/2)" />
                            <line number="20" hits="0" branch="false" />
                          </lines>
                        </class>
                      </classes>
                    </package>
                  </packages>
                </coverage>
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("coverage.xml", "coverage", "cobertura", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("src/App.py", file.filePath());
        assertEquals(new CoverageMetric(1, 2), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 2), file.function());
        assertEquals(new CoverageMetric(1, 2), file.statement());
    }

    @Test
    void supportsCoveragePyXmlAlias() {
        CoberturaCoverageParser parser = new CoberturaCoverageParser(new SecureXmlCoverageDocumentReader());

        assertTrue(parser.supports(new CoverageInputArtifact(
                "coverage.xml",
                "coverage",
                "coverage.py-xml",
                "bucket",
                "path",
                "sha")));
    }
}
```

- [ ] **Step 2: Run the failing Cobertura parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=CoberturaCoverageParserTest test
```

Expected: FAIL because the parser does not exist.

- [ ] **Step 3: Implement Cobertura parser**

Create `CoberturaCoverageParser` with these rules:

```java
public boolean supports(CoverageInputArtifact artifact) {
    String format = artifact.normalizedFormat();
    String name = artifact.normalizedName();
    return "cobertura".equals(format)
            || "cobertura-xml".equals(format)
            || "coverage.py-xml".equals(format)
            || "coveragepy-xml".equals(format)
            || name.endsWith("cobertura.xml");
}
```

Parsing rules:

- Iterate `class` elements.
- Use `filename` for the normalized file path.
- Read direct child `lines` under each `class`, then direct child `line` elements under that `lines` element.
- A line is executable when it has a `number`; it is covered when `hits > 0`.
- Add one statement ID per executable line: `lineNumber`.
- For branch lines, parse `condition-coverage` values like `50% (1/2)`; create `total` branch IDs as `lineNumber:index` and cover the first `covered` branch IDs.
- Read `method` elements under class `methods`.
- Function key format is `methodName + signature + "@" + firstLineNumber`.
- A function is covered when any method line has `hits > 0` or `line-rate` parses to a value greater than zero.
- Throw `IllegalStateException("No Cobertura file records found in " + artifact.name())` when no files are found.

- [ ] **Step 4: Register Cobertura parser**

In `AnalysisComponents`, construct the registry with:

```java
new CoverageParserRegistry(List.of(
        new LcovCoverageParser(),
        new CoberturaCoverageParser(new SecureXmlCoverageDocumentReader())))
```

- [ ] **Step 5: Run Cobertura parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=CoberturaCoverageParserTest test
```

Expected: PASS.

---

### Task 6: Add JaCoCo XML Parser

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/JacocoCoverageParser.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/JacocoCoverageParserTest.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`

- [ ] **Step 1: Write failing JaCoCo parser test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/JacocoCoverageParserTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacocoCoverageParserTest {
    @Test
    void parsesSourcefileLinesBranchesMethodsAndStatements() {
        JacocoCoverageParser parser = new JacocoCoverageParser(new SecureXmlCoverageDocumentReader());
        byte[] content = """
                <report name="unit">
                  <package name="dev/vericov">
                    <class name="dev/vericov/App" sourcefilename="App.java">
                      <method name="hit" desc="()V" line="10">
                        <counter type="METHOD" missed="0" covered="1" />
                      </method>
                      <method name="miss" desc="()V" line="20">
                        <counter type="METHOD" missed="1" covered="0" />
                      </method>
                    </class>
                    <sourcefile name="App.java">
                      <line nr="10" mi="0" ci="4" mb="1" cb="1" />
                      <line nr="20" mi="2" ci="0" mb="0" cb="0" />
                    </sourcefile>
                  </package>
                </report>
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("jacoco.xml", "coverage", "jacoco", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("dev/vericov/App.java", file.filePath());
        assertEquals(new CoverageMetric(1, 2), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 2), file.function());
        assertEquals(new CoverageMetric(1, 2), file.statement());
    }
}
```

- [ ] **Step 2: Run the failing JaCoCo parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=JacocoCoverageParserTest test
```

Expected: FAIL because the parser does not exist.

- [ ] **Step 3: Implement JaCoCo parser**

Create `JacocoCoverageParser` with these support rules:

```java
return "jacoco".equals(format)
        || "jacoco-xml".equals(format)
        || name.endsWith("jacoco.xml");
```

Parsing rules:

- Iterate `package` elements.
- File path is `package.name + "/" + sourcefile.name`, without a leading slash when package name is blank.
- For each `sourcefile/line`, executable line total is `mi + ci > 0`; covered line is `ci > 0`.
- Statement IDs use `lineNumber`; covered statements use `ci > 0`.
- Branch total is `mb + cb`; covered branch count is `cb`; branch IDs use `lineNumber:index`.
- For each `class` in the package, match methods to file path by `sourcefilename`.
- Function key format is `className + "#" + methodName + desc + "@" + line`.
- A method is covered when its direct `counter type="METHOD"` has `covered > 0`.
- Throw `IllegalStateException("No JaCoCo file records found in " + artifact.name())` when no files are found.

- [ ] **Step 4: Register JaCoCo parser**

Add `new JacocoCoverageParser(new SecureXmlCoverageDocumentReader())` to the registry list in `AnalysisComponents` and test helpers that construct full parser registries.

- [ ] **Step 5: Run JaCoCo parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=JacocoCoverageParserTest test
```

Expected: PASS.

---

### Task 7: Add Clover XML Parser

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CloverCoverageParser.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CloverCoverageParserTest.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`

- [ ] **Step 1: Write failing Clover parser test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CloverCoverageParserTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloverCoverageParserTest {
    @Test
    void parsesFileLineConditionAndMethodCoverage() {
        CloverCoverageParser parser = new CloverCoverageParser(new SecureXmlCoverageDocumentReader());
        byte[] content = """
                <coverage generated="1">
                  <project>
                    <file path="./src/App.java" name="App.java">
                      <line num="10" type="method" name="run" count="1" />
                      <line num="11" type="stmt" count="0" />
                      <line num="12" type="cond" count="1" truecount="1" falsecount="0" />
                    </file>
                  </project>
                </coverage>
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("clover.xml", "coverage", "clover", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("src/App.java", file.filePath());
        assertEquals(new CoverageMetric(2, 3), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 1), file.function());
        assertEquals(new CoverageMetric(2, 3), file.statement());
    }
}
```

- [ ] **Step 2: Run the failing Clover parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=CloverCoverageParserTest test
```

Expected: FAIL because the parser does not exist.

- [ ] **Step 3: Implement Clover parser**

Create `CloverCoverageParser` with these support rules:

```java
return "clover".equals(format)
        || "clover-xml".equals(format)
        || name.endsWith("clover.xml");
```

Parsing rules:

- Iterate `file` elements.
- Use `path` when present; use `name` when `path` is blank.
- For each direct `line` element with a numeric `num`, add that line to executable lines.
- A line is covered when `count > 0`.
- Every executable line contributes statement ID `lineNumber`; covered statements mirror covered lines for Clover line records.
- `type="method"` adds a function key of `name + "@" + lineNumber`; it is covered when `count > 0`.
- `type="cond"` adds two branch IDs: `lineNumber:true` and `lineNumber:false`; cover each side when `truecount > 0` or `falsecount > 0`.
- Throw `IllegalStateException("No Clover file records found in " + artifact.name())` when no files are found.

- [ ] **Step 4: Register Clover parser**

Add `new CloverCoverageParser(new SecureXmlCoverageDocumentReader())` to the registry list in `AnalysisComponents` and full-registry test helpers.

- [ ] **Step 5: Run Clover parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=CloverCoverageParserTest test
```

Expected: PASS.

---

### Task 8: Add Go Cover Profile Parser

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/GoCoverProfileParser.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/GoCoverProfileParserTest.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`

- [ ] **Step 1: Write failing Go cover profile parser test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/GoCoverProfileParserTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoCoverProfileParserTest {
    @Test
    void parsesLineAndStatementCoverageFromCoverProfile() {
        GoCoverProfileParser parser = new GoCoverProfileParser();
        byte[] content = """
                mode: atomic
                github.com/acme/app/foo.go:10.1,10.20 2 1
                github.com/acme/app/foo.go:11.1,12.2 1 0
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("cover.out", "coverage", "go-coverprofile", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("github.com/acme/app/foo.go", file.filePath());
        assertEquals(new CoverageMetric(1, 3), file.line());
        assertEquals(new CoverageMetric(2, 3), file.statement());
        assertEquals(new CoverageMetric(0, 0), file.branch());
        assertEquals(new CoverageMetric(0, 0), file.function());
    }
}
```

- [ ] **Step 2: Run the failing Go parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=GoCoverProfileParserTest test
```

Expected: FAIL because the parser does not exist.

- [ ] **Step 3: Implement Go cover profile parser**

Create `GoCoverProfileParser` with these support rules:

```java
return "go".equals(format)
        || "go-cover".equals(format)
        || "go-coverprofile".equals(format)
        || "gocover".equals(format)
        || name.endsWith(".coverprofile")
        || name.endsWith("cover.out");
```

Parsing rules:

- Ignore blank lines and the required `mode:` header.
- Parse records shaped as `path:startLine.startCol,endLine.endCol numberOfStatements count`.
- Add every line from `startLine` through `endLine` to executable lines; when `count > 0`, add every line in the range to covered lines.
- Create statement IDs as `startLine.startCol-endLine.endCol:index` for `index` from `0` to `numberOfStatements - 1`.
- Add all statement IDs to `statements`; when `count > 0`, add them to `coveredStatements`.
- Leave branch and function sets empty because Go profiles do not encode them.
- Throw `IllegalStateException("No Go coverage records found in " + artifact.name())` when no file records are found.

- [ ] **Step 4: Register Go parser**

Add `new GoCoverProfileParser()` to the registry list in `AnalysisComponents` and full-registry test helpers.

- [ ] **Step 5: Run Go parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=GoCoverProfileParserTest test
```

Expected: PASS.

---

### Task 9: Add gcov/llvm-cov gcov Text Parser

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/GcovCoverageParser.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/GcovCoverageParserTest.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`

- [ ] **Step 1: Write failing gcov parser test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/GcovCoverageParserTest.java`:

```java
package dev.vericov.analysis.coverage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GcovCoverageParserTest {
    @Test
    void parsesLineBranchAndFunctionCoverageFromGcovText() {
        GcovCoverageParser parser = new GcovCoverageParser();
        byte[] content = """
                    -:    0:Source:src/main.c
                    1:   10:int main(void) {
                #####:   11:  if (flag) {
                branch  0 never executed
                    3:   12:  return 0;
                branch  0 taken 100%
                function main called 1 returned 100% blocks executed 100%
                    -:   13:}
                """.getBytes(StandardCharsets.UTF_8);

        ParsedCoverage parsed = parser.parse(
                new CoverageInputArtifact("main.c.gcov", "coverage", "gcov", "bucket", "path", "sha"),
                content);

        ParsedCoverageFile file = parsed.files().getFirst();
        assertEquals("src/main.c", file.filePath());
        assertEquals(new CoverageMetric(2, 3), file.line());
        assertEquals(new CoverageMetric(1, 2), file.branch());
        assertEquals(new CoverageMetric(1, 1), file.function());
        assertEquals(new CoverageMetric(2, 3), file.statement());
    }
}
```

- [ ] **Step 2: Run the failing gcov parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=GcovCoverageParserTest test
```

Expected: FAIL because the parser does not exist.

- [ ] **Step 3: Implement gcov parser**

Create `GcovCoverageParser` with these support rules:

```java
return "gcov".equals(format)
        || "llvm-cov".equals(format)
        || "llvm-cov-gcov".equals(format)
        || name.endsWith(".gcov");
```

Parsing rules:

- Parse source metadata lines matching `count:lineNumber:Source:path`; use the source path as the file path.
- For source lines, treat counts `-` and empty counts as non-executable, `#####` and `=====` as executable uncovered, and numeric counts as executable covered when the number is greater than zero.
- Track the most recent executable source line as `currentLineNumber`.
- Every executable source line contributes statement ID `lineNumber`; covered statements mirror covered source lines.
- Parse `branch N taken X%` and `branch N never executed` lines against `currentLineNumber`; branch key is `currentLineNumber + ":" + branchNumber`.
- A branch is covered when it has a numeric taken percentage greater than zero.
- Parse `function NAME called COUNT` lines; function key is `NAME`; covered when `COUNT > 0`.
- If the report has no `Source:` line, derive a fallback file path from the artifact name by removing a trailing `.gcov`.
- Throw `IllegalStateException("No gcov coverage records found in " + artifact.name())` when no executable records are found.

- [ ] **Step 4: Register gcov parser**

Add `new GcovCoverageParser()` to the registry list in `AnalysisComponents` and full-registry test helpers.

- [ ] **Step 5: Run gcov parser test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=GcovCoverageParserTest test
```

Expected: PASS.

---

### Task 10: Add Mixed-Format Processor And BDD Coverage

**Files:**
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`
- Modify: `services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java`

- [ ] **Step 1: Add full registry test helper**

In `DefaultCoverageAnalysisProcessorTest`, add:

```java
private static CoverageParserRegistry fullParserRegistry() {
    SecureXmlCoverageDocumentReader xmlReader = new SecureXmlCoverageDocumentReader();
    return new CoverageParserRegistry(List.of(
            new LcovCoverageParser(),
            new CoberturaCoverageParser(xmlReader),
            new JacocoCoverageParser(xmlReader),
            new CloverCoverageParser(xmlReader),
            new GoCoverProfileParser(),
            new GcovCoverageParser()));
}
```

- [ ] **Step 2: Add mixed-format processor test**

Add a processor test with one LCOV artifact and one JaCoCo artifact:

```java
@Test
void downloadsMixedCoverageArtifactsAndPersistsMergedCoverageReport() {
    FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
            UPLOAD_ID,
            TENANT_ID,
            REPOSITORY_ID,
            "abc123",
            "main",
            42,
            List.of(
                    new CoverageInputArtifact(
                            "unit.lcov",
                            "coverage",
                            "lcov",
                            "coverage-raw",
                            "tenant/upload/coverage/unit.lcov",
                            "sha-1"),
                    new CoverageInputArtifact(
                            "jacoco.xml",
                            "coverage",
                            "jacoco",
                            "coverage-raw",
                            "tenant/upload/coverage/jacoco.xml",
                            "sha-2"))));
    FakeContentStore content = new FakeContentStore(Map.of(
            "coverage-raw/tenant/upload/coverage/unit.lcov", """
                    TN:
                    SF:src/App.java
                    DA:1,1
                    DA:2,0
                    end_of_record
                    """.getBytes(StandardCharsets.UTF_8),
            "coverage-raw/tenant/upload/coverage/jacoco.xml", """
                    <report name="unit">
                      <package name="src">
                        <class name="src/App" sourcefilename="App.java">
                          <method name="run" desc="()V" line="2">
                            <counter type="METHOD" missed="0" covered="1" />
                          </method>
                        </class>
                        <sourcefile name="App.java">
                          <line nr="2" mi="0" ci="1" mb="0" cb="0" />
                          <line nr="3" mi="0" ci="1" mb="0" cb="0" />
                        </sourcefile>
                      </package>
                    </report>
                    """.getBytes(StandardCharsets.UTF_8)));
    FakeReportRepository reports = new FakeReportRepository();
    DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
            inputs,
            content,
            reports,
            fullParserRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    processor.process(event());

    CoverageReport report = reports.savedReport;
    assertEquals(3, report.line().total());
    assertEquals(3, report.line().covered());
    assertEquals(1, report.function().total());
    assertEquals(1, report.function().covered());
    assertEquals(3, report.statement().total());
    assertEquals(3, report.statement().covered());
}
```

- [ ] **Step 3: Add mixed-format BDD scenario**

In `coverage-analysis.feature`, add:

```gherkin
    Scenario: Mixed coverage artifact formats are merged
      Given an upload received message with LCOV and JaCoCo coverage artifacts
      And object storage contains the mixed coverage shards
      When the analysis worker polls once
      Then the coverage report is persisted with 3 covered lines out of 3
      And the analysis job is completed
      And the queue message is archived
```

- [ ] **Step 4: Add BDD step definitions**

In `AnalysisSteps`, add a `fullParserRegistry()` helper matching the processor test and update `processor()` to use it. Add step definitions for the mixed upload and mixed shard content using the same LCOV and JaCoCo payloads from the processor test.

- [ ] **Step 5: Run processor and BDD tests**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=DefaultCoverageAnalysisProcessorTest,RunAnalysisFeaturesTest test
```

Expected: PASS.

---

### Task 11: Update Coverage Analysis Documentation

**Files:**
- Modify: `docs/backend/services/04-coverage-analysis-service.md`

- [ ] **Step 1: Replace the initial parser support section**

Replace the LCOV-only list under `Initial parser support` with:

```markdown
Initial parser support:

- LCOV line coverage from `DA`
- LCOV branch coverage from `BRDA`
- LCOV function coverage from `FN` and `FNDA`
- Cobertura XML line coverage from `class/lines/line`
- Cobertura XML branch coverage from `condition-coverage`
- Cobertura XML function coverage from `method` entries
- coverage.py XML through the Cobertura-compatible parser
- JaCoCo XML line and branch coverage from `sourcefile/line`
- JaCoCo XML function coverage from `class/method` counters
- Clover XML line coverage from `file/line`
- Clover XML branch coverage from `type="cond"` true/false counts
- Clover XML function coverage from `type="method"` lines
- Go cover profile line and statement coverage from block records
- gcov and llvm-cov gcov text line, branch, and function coverage

Statement coverage is represented independently when the source format exposes statement counts. LCOV, Cobertura, Clover, and gcov mirror statement coverage from executable line records; Go profiles use block statement counts.
```

- [ ] **Step 2: Add parser backlog note**

Add this paragraph after the support list:

```markdown
Remaining parser families from the PRD are Istanbul/nyc JSON, coverage.py JSON, and the generic JSON adapter. Those require a stable JSON canonical import contract and are tracked separately from the XML/text parser expansion.
```

- [ ] **Step 3: Review the doc diff**

Run:

```bash
git diff -- docs/backend/services/04-coverage-analysis-service.md
```

Expected: diff shows only the parser support update and backlog note.

---

### Task 12: Final Verification

**Files:**
- No new source edits in this task.

- [ ] **Step 1: Run coverage-analysis test suite**

Run:

```bash
mvn -pl services/coverage-analysis test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run root Maven tests**

Run:

```bash
mvn test
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Inspect final diff**

Run:

```bash
git diff --stat
git diff -- services/coverage-analysis docs/backend/services/04-coverage-analysis-service.md
```

Expected: source changes are limited to the parser pipeline, parser implementations, parser tests, BDD fixture updates, and the Coverage Analysis service docs.

- [ ] **Step 4: Commit**

Run:

```bash
git add services/coverage-analysis docs/backend/services/04-coverage-analysis-service.md
git commit -m "feat: add coverage report parsers"
```

Expected: commit succeeds after tests pass.

---

## Implementation Notes

- Reuse the same `SecureXmlCoverageDocumentReader` instance when constructing XML parsers in `AnalysisComponents`; it is stateless.
- Prefer immutable outputs from parsers: build mutable accumulator sets locally, then pass them into `ParsedCoverageFile`, which copies them.
- Keep parser failures format-specific and artifact-specific so analysis job retry/dead-letter records are useful.
- Do not add a third-party XML or coverage parsing dependency unless a report family cannot be handled correctly with Java DOM and focused helpers.
- Do not broaden upload ingestion format validation in this change; the processor remains the source of parser truth until upload API format policy is designed.

## Self-Review

- Spec coverage: The plan covers the user-requested missing parser families: Cobertura, JaCoCo, gcov/llvm-cov, and Clover. It also covers Go profiles because they are listed in the PRD's initial supported report families and require the same parser registry work.
- Scope coverage: LCOV remains supported and is migrated into the parser contract instead of being replaced.
- Security coverage: XML parsing has a dedicated XXE rejection test.
- Type consistency: All parser classes implement `CoverageParser`, all processor wiring uses `CoverageParserRegistry`, and statement coverage is represented by `statements` and `coveredStatements` on `ParsedCoverageFile`.
- Deferred work: Istanbul/nyc JSON, coverage.py JSON, and generic JSON adapters are explicitly documented as separate parser-family work because they need a JSON canonical import contract.
