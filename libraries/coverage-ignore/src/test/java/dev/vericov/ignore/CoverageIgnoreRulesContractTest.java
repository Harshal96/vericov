package dev.vericov.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CoverageIgnoreRulesContractTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchCases")
    void matchesSharedContract(String name, List<String> rules, String path, boolean ignored) {
        assertEquals(ignored, new CoverageIgnoreRules(rules).isIgnored(path), name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCases")
    void rejectsSharedInvalidRuleContract(String name, String rule, String errorCode) {
        InvalidCoverageIgnoreRuleException exception = assertThrows(
                InvalidCoverageIgnoreRuleException.class,
                () -> new CoverageIgnoreRules(List.of(rule)));

        assertEquals(errorCode, exception.code(), name);
        assertEquals(0, exception.index(), name);
    }

    @Test
    void rulesAreImmutableAndPreserveOrder() {
        List<String> source = new ArrayList<>(List.of("vendor/**", "!vendor/maintained/**"));

        CoverageIgnoreRules matcher = new CoverageIgnoreRules(source);
        Collections.reverse(source);

        assertEquals(List.of("vendor/**", "!vendor/maintained/**"), matcher.rules());
        assertThrows(UnsupportedOperationException.class, () -> matcher.rules().add("generated/**"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("componentMatchCases")
    void matchesComponentPathContract(
            String name,
            String glob,
            String path,
            boolean matches,
            int literalSegments,
            int literalCharacters) {
        CoveragePathPattern pattern = new CoveragePathPattern(glob);

        assertEquals(matches, pattern.matches(path), name);
        assertEquals(literalSegments, pattern.specificity().literalSegments(), name);
        assertEquals(literalCharacters, pattern.specificity().literalCharacters(), name);
    }

    @Test
    void componentPathsRejectNegation() {
        assertEquals(
                "negation_not_allowed",
                assertThrows(
                                InvalidCoverageIgnoreRuleException.class,
                                () -> new CoveragePathPattern("!src/**"))
                        .code());
    }

    private static Stream<Object[]> matchCases() throws IOException {
        return rows("coverage-ignore-matches.tsv")
                .map(columns -> new Object[] {
                    columns[0],
                    columns[1].isEmpty() ? List.of() : Arrays.asList(columns[1].split(";;", -1)),
                    columns[2],
                    Boolean.parseBoolean(columns[3])
                });
    }

    private static Stream<Object[]> invalidCases() throws IOException {
        return rows("coverage-ignore-invalid.tsv")
                .map(columns -> new Object[] {
                    columns[0],
                    switch (columns[1]) {
                        case "<empty>" -> "";
                        case "<spaces>" -> "   ";
                        default -> columns[1];
                    },
                    columns[2]
                });
    }

    private static Stream<Object[]> componentMatchCases() throws IOException {
        return rows("component-path-matches.tsv")
                .map(columns -> new Object[] {
                    columns[0],
                    columns[1],
                    columns[2],
                    Boolean.parseBoolean(columns[3]),
                    Integer.parseInt(columns[4]),
                    Integer.parseInt(columns[5])
                });
    }

    private static Stream<String[]> rows(String fileName) throws IOException {
        return Files.lines(contractRoot().resolve(fileName))
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split("\t", -1));
    }

    private static Path contractRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("test-contracts");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("test-contracts directory not found");
    }
}
