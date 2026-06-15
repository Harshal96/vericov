package dev.vericov.analysis.coverage;

import dev.vericov.analysis.gaps.CoverageGapFinding;
import dev.vericov.componentconfig.ComponentAssignment;
import dev.vericov.componentconfig.ComponentConfigSnapshot;
import dev.vericov.componentconfig.ComponentResolver;
import dev.vericov.componentconfig.ResolvedComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComponentCoverageCalculator {
    public CoverageReport calculate(CoverageReport report, ComponentConfigSnapshot snapshot) {
        ComponentResolver resolver = new ComponentResolver(snapshot);
        Map<String, Accumulator> accumulators = new LinkedHashMap<>();
        for (ResolvedComponent component : snapshot.resolvedComponents()) {
            accumulators.put(component.key(), new Accumulator(component));
        }

        List<CoverageFileSummary> files = new ArrayList<>();
        int unassignedCount = 0;
        for (CoverageFileSummary file : report.files()) {
            ComponentAssignment assignment = resolver.resolve(file.filePath()).orElse(null);
            if (assignment == null) {
                unassignedCount++;
                Accumulator accumulator = accumulators.computeIfAbsent(
                        "unassigned",
                        ignored -> Accumulator.unassigned());
                accumulator.add(file, true);
                files.add(copy(file, "unassigned", List.of()));
                continue;
            }
            for (String componentKey : assignment.componentPath()) {
                accumulators.get(componentKey).add(
                        file,
                        componentKey.equals(assignment.leafComponentKey()));
            }
            files.add(copy(file, assignment.leafComponentKey(), assignment.owners()));
        }

        List<CoverageComponentRollup> rollups = accumulators.values().stream()
                .map(Accumulator::rollup)
                .toList();
        List<String> warnings = unassignedCount == 0
                ? List.of()
                : List.of("unassigned_files:" + unassignedCount);
        return report.withComponentCoverage(files, rollups, warnings);
    }

    public CoverageReport applyFindings(CoverageReport report) {
        Map<String, RiskAccumulator> risk = new LinkedHashMap<>();
        Map<String, CoverageComponentRollup> rollups = new LinkedHashMap<>();
        report.componentRollups().forEach(rollup -> rollups.put(rollup.componentKey(), rollup));
        for (CoverageGapFinding finding : report.gapFindings()) {
            if (finding.componentKey() == null) {
                continue;
            }
            CoverageComponentRollup leaf = rollups.get(finding.componentKey());
            List<String> path = leaf == null
                    ? List.of(finding.componentKey())
                    : leaf.componentPath();
            for (String key : path) {
                risk.computeIfAbsent(key, ignored -> new RiskAccumulator()).add(finding);
            }
        }
        List<CoverageComponentRollup> updated = report.componentRollups().stream()
                .map(rollup -> {
                    RiskAccumulator accumulator = risk.get(rollup.componentKey());
                    return accumulator == null
                            ? rollup
                            : rollup.withRisk(
                                    accumulator.gapCount,
                                    accumulator.debtCount,
                                    accumulator.riskScore,
                                    accumulator.highestRisk);
                })
                .toList();
        return report.withComponentRollups(updated);
    }

    private static CoverageFileSummary copy(
            CoverageFileSummary file,
            String componentKey,
            List<String> owners) {
        return new CoverageFileSummary(
                file.filePath(),
                file.line(),
                file.branch(),
                file.function(),
                file.statement(),
                componentKey,
                file.packageName(),
                owners);
    }

    private static final class Accumulator {
        private final String key;
        private final String parentKey;
        private final List<String> path;
        private final int depth;
        private final int position;
        private final String name;
        private final List<String> owners;
        private final Map<String, BigDecimal> gates;
        private int lineCovered;
        private int lineTotal;
        private int branchCovered;
        private int branchTotal;
        private int functionCovered;
        private int functionTotal;
        private int statementCovered;
        private int statementTotal;
        private int directFiles;
        private int descendantFiles;

        private Accumulator(ResolvedComponent component) {
            key = component.key();
            parentKey = component.parentKey();
            path = component.componentPath();
            depth = component.depth();
            position = component.position();
            name = component.name();
            owners = component.owners();
            gates = component.effectiveGates();
        }

        private Accumulator() {
            key = "unassigned";
            parentKey = null;
            path = List.of("unassigned");
            depth = 0;
            position = Integer.MAX_VALUE;
            name = "Unassigned";
            owners = List.of();
            gates = Map.of();
        }

        private static Accumulator unassigned() {
            return new Accumulator();
        }

        private void add(CoverageFileSummary file, boolean direct) {
            lineCovered += file.line().covered();
            lineTotal += file.line().total();
            branchCovered += file.branch().covered();
            branchTotal += file.branch().total();
            functionCovered += file.function().covered();
            functionTotal += file.function().total();
            statementCovered += file.statement().covered();
            statementTotal += file.statement().total();
            descendantFiles++;
            if (direct) {
                directFiles++;
            }
        }

        private CoverageComponentRollup rollup() {
            return new CoverageComponentRollup(
                    key,
                    parentKey,
                    path,
                    depth,
                    position,
                    name,
                    owners,
                    gates,
                    new CoverageMetric(lineCovered, lineTotal),
                    new CoverageMetric(branchCovered, branchTotal),
                    new CoverageMetric(functionCovered, functionTotal),
                    new CoverageMetric(statementCovered, statementTotal),
                    directFiles,
                    descendantFiles,
                    0,
                    0,
                    BigDecimal.ZERO,
                    null);
        }
    }

    private static final class RiskAccumulator {
        private int gapCount;
        private int debtCount;
        private BigDecimal riskScore = BigDecimal.ZERO;
        private String highestRisk;

        private void add(CoverageGapFinding finding) {
            if ("debt_suppressed".equals(finding.status())) {
                debtCount++;
            } else if ("active".equals(finding.status())) {
                gapCount++;
                riskScore = riskScore.add(finding.riskScore());
                highestRisk = higherRisk(highestRisk, finding.riskLevel());
            }
        }

        private static String higherRisk(String current, String candidate) {
            if (current == null || rank(candidate) > rank(current)) {
                return candidate;
            }
            return current;
        }

        private static int rank(String level) {
            return switch (level) {
                case "critical" -> 4;
                case "high" -> 3;
                case "medium" -> 2;
                case "low" -> 1;
                default -> 0;
            };
        }
    }
}
