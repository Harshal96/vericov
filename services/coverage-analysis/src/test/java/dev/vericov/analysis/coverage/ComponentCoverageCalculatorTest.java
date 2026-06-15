package dev.vericov.analysis.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vericov.componentconfig.ComponentConfigSnapshot;
import dev.vericov.componentconfig.ComponentDefinition;
import dev.vericov.componentconfig.ComponentGates;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ComponentCoverageCalculatorTest {
    @Test
    void assignsLeavesBuildsAncestorRollupsAndReportsUnassignedFiles() {
        CoverageReport report = report(List.of(
                file("services/payments/api/App.java", 8, 10),
                file("services/payments/web/Page.java", 3, 5),
                file("README.md", 0, 0)));

        CoverageReport calculated = new ComponentCoverageCalculator().calculate(report, snapshot());

        assertEquals(
                List.of("payments-api", "payments-web", "unassigned"),
                calculated.files().stream().map(CoverageFileSummary::leafComponentKey).toList());
        assertEquals(List.of("unassigned_files:1"), calculated.warnings());
        assertEquals(
                List.of("commerce", "payments", "payments-api", "payments-web", "unassigned"),
                calculated.componentRollups().stream().map(CoverageComponentRollup::componentKey).toList());

        CoverageComponentRollup commerce = calculated.componentRollups().getFirst();
        assertEquals(new CoverageMetric(11, 15), commerce.line());
        assertEquals(0, commerce.directFileCount());
        assertEquals(2, commerce.descendantFileCount());

        CoverageComponentRollup api = calculated.componentRollups().get(2);
        assertEquals(new CoverageMetric(8, 10), api.line());
        assertEquals(1, api.directFileCount());
        assertEquals(1, api.descendantFileCount());
        assertEquals(List.of("team-payments"), api.owners());
        assertEquals(
                Map.of("branch", BigDecimal.valueOf(70), "line", BigDecimal.valueOf(90)),
                api.effectiveGates());

        CoverageComponentRollup unassigned = calculated.componentRollups().getLast();
        assertEquals(new CoverageMetric(0, 0), unassigned.line());
        assertEquals(1, unassigned.directFileCount());
    }

    @Test
    void includesConfiguredComponentsWithNoMatchingFiles() {
        CoverageReport calculated = new ComponentCoverageCalculator().calculate(report(List.of()), snapshot());

        assertEquals(4, calculated.componentRollups().size());
        assertEquals(
                List.of(new CoverageMetric(0, 0), new CoverageMetric(0, 0), new CoverageMetric(0, 0), new CoverageMetric(0, 0)),
                calculated.componentRollups().stream().map(CoverageComponentRollup::line).toList());
        assertEquals(List.of(), calculated.warnings());
    }

    public static ComponentConfigSnapshot snapshot() {
        ComponentDefinition api = new ComponentDefinition(
                "payments-api",
                "Payments API",
                List.of("team-payments"),
                new ComponentGates(Map.of("line", BigDecimal.valueOf(90))),
                List.of("services/payments/api/**"),
                List.of());
        ComponentDefinition web = new ComponentDefinition(
                "payments-web",
                "Payments Web",
                null,
                ComponentGates.empty(),
                List.of("services/payments/web/**"),
                List.of());
        ComponentDefinition payments = new ComponentDefinition(
                "payments",
                "Payments",
                null,
                ComponentGates.empty(),
                List.of(),
                List.of(api, web));
        ComponentDefinition commerce = new ComponentDefinition(
                "commerce",
                "Commerce",
                List.of("team-commerce"),
                new ComponentGates(Map.of(
                        "line", BigDecimal.valueOf(80),
                        "branch", BigDecimal.valueOf(70))),
                List.of(),
                List.of(payments));
        return new ComponentConfigSnapshot(1, List.of(), List.of(commerce));
    }

    private static CoverageFileSummary file(String path, int covered, int total) {
        CoverageMetric metric = new CoverageMetric(covered, total);
        return new CoverageFileSummary(path, metric, new CoverageMetric(0, 0), new CoverageMetric(0, 0), metric);
    }

    private static CoverageReport report(List<CoverageFileSummary> files) {
        CoverageMetric line = new CoverageMetric(
                files.stream().mapToInt(file -> file.line().covered()).sum(),
                files.stream().mapToInt(file -> file.line().total()).sum());
        return new CoverageReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "abc123",
                "main",
                null,
                line,
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                line,
                files,
                List.of(),
                Instant.parse("2026-06-15T00:00:00Z"));
    }
}
