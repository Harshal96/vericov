package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffCoverageFile;
import dev.vericov.analysis.diff.DiffCoverageLine;
import dev.vericov.analysis.diff.DiffCoverageReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

public class GateEvaluator {

    public List<GateEvaluation> evaluate(
            CoverageReport report,
            List<GateConfiguration> gates,
            Instant evaluatedAt) {
        List<GateConfiguration> filteredGates = gates.stream()
                .filter(gate -> "project_coverage".equals(gate.gateType()))
                .toList();
        return evaluate(report, filteredGates, new RepositoryContext("ctx-default", List.of(), List.of(), Map.of()), null, evaluatedAt);
    }

    public List<GateEvaluation> evaluate(
            CoverageReport report,
            List<GateConfiguration> gates,
            RepositoryContext repositoryContext,
            DiffCoverageReport diffCoverage,
            Instant evaluatedAt) {
        return gates.stream()
                .filter(GateConfiguration::active)
                .map(gate -> evaluateGate(report, gate, repositoryContext, diffCoverage, evaluatedAt))
                .toList();
    }

    private GateEvaluation evaluateGate(
            CoverageReport report,
            GateConfiguration gate,
            RepositoryContext repositoryContext,
            DiffCoverageReport diffCoverage,
            Instant evaluatedAt) {
        String gateType = gate.gateType();
        return switch (gateType) {
            case "project_coverage" -> evaluateProjectCoverageGate(report, gate, repositoryContext, evaluatedAt);
            case "patch_coverage" -> evaluatePatchCoverageGate(report, gate, repositoryContext, diffCoverage, evaluatedAt);
            case "component_coverage" -> evaluateComponentCoverageGate(report, gate, repositoryContext, evaluatedAt);
            case "coverage_drop" -> evaluateCoverageDropGate(report, gate, repositoryContext, diffCoverage, evaluatedAt);
            default -> evaluation(report, gate, null, "warning", Map.of(
                    "scope", "project",
                    "reason", "unsupported_gate_type_" + gateType,
                    "coverage_report_id", report.reportId().toString()), evaluatedAt);
        };
    }

    private GateEvaluation evaluateProjectCoverageGate(
            CoverageReport report,
            GateConfiguration gate,
            RepositoryContext repositoryContext,
            Instant evaluatedAt) {
        Map<String, Object> config = gate.config() != null ? gate.config() : Map.of();
        Map<String, Object> debtConfig = getDebtConfig(config);
        String debtMode = (String) debtConfig.getOrDefault("mode", "none");
        boolean failOnExpiredDebt = getBoolean(debtConfig, "fail_on_expired_debt", true);

        // Scope
        Map<String, Object> scopeConfig = (Map<String, Object>) config.getOrDefault("scope", Map.of());
        String pathPattern = (String) scopeConfig.get("path_pattern");
        String componentId = (String) scopeConfig.get("component_id");

        String scopeName = "project";
        CoverageMetric metric;
        if (pathPattern != null) {
            scopeName = "path";
            int covered = 0;
            int total = 0;
            for (CoverageFileSummary file : report.files()) {
                if (DebtItem.matchPath(pathPattern, file.filePath())) {
                    CoverageMetric fileMetric = getFileMetric(file, gate.metric());
                    covered += fileMetric.covered();
                    total += fileMetric.total();
                }
            }
            metric = new CoverageMetric(covered, total);
        } else if (componentId != null) {
            scopeName = "component";
            ComponentRollup rollup = repositoryContext.componentRollups().get(componentId);
            if (rollup == null) {
                return evaluation(report, gate, null, "warning", Map.of(
                        "scope", "component",
                        "reason", "component_rollup_not_found",
                        "coverage_report_id", report.reportId().toString()), evaluatedAt);
            }
            metric = getRollupMetric(rollup, gate.metric());
        } else {
            Optional<CoverageMetric> optMetric = reportMetric(report, gate.metric());
            if (optMetric.isEmpty()) {
                return evaluation(report, gate, null, "warning", Map.of(
                        "scope", "project",
                        "reason", "metric_not_available_in_coverage_report",
                        "coverage_report_id", report.reportId().toString()), evaluatedAt);
            }
            metric = optMetric.get();
        }

        BigDecimal rawPercentage = percentage(metric);
        String rawStatus = rawPercentage.compareTo(normalize(gate.threshold())) >= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";

        // Debt matching
        List<UUID> suppressedFindingIds = new ArrayList<>();
        List<UUID> suppressedDebtIds = new ArrayList<>();
        List<UUID> expiredDebtIds = new ArrayList<>();
        List<UUID> reappearedFindingIds = new ArrayList<>();

        int rawCovered = metric.covered();
        int rawTotal = metric.total();
        int adjustedTotal = rawTotal;

        boolean expiredDebtFailed = false;

        if ("adjust_metric".equalsIgnoreCase(debtMode)) {
            // we adjust the metric total by subtracting matching active/unexpired debt lines from the denominator.
            long waivedLines = report.lineHits().stream()
                    .filter(hit -> hit.hits() == 0)
                    .filter(hit -> pathPattern == null || DebtItem.matchPath(pathPattern, hit.filePath()))
                    .filter(hit -> {
                        for (DebtItem debtItem : repositoryContext.debtItems()) {
                            if (debtItem.matches(new Finding(UUID.randomUUID(), hit.filePath(), hit.lineNumber(), "low", "active"))) {
                                if (isActive(debtItem, evaluatedAt)) {
                                    if (!suppressedDebtIds.contains(debtItem.id())) {
                                        suppressedDebtIds.add(debtItem.id());
                                    }
                                    return true;
                                }
                            }
                        }
                        return false;
                    })
                    .count();

            adjustedTotal = Math.max(0, rawTotal - (int) waivedLines);
        }

        // Expired debt check
        for (DebtItem debtItem : repositoryContext.debtItems()) {
            if (isExpired(debtItem, evaluatedAt)) {
                boolean matchesScope = pathPattern == null || DebtItem.matchPath(pathPattern, debtItem.filePath());
                if (matchesScope) {
                    expiredDebtIds.add(debtItem.id());
                    if (failOnExpiredDebt) {
                        expiredDebtFailed = true;
                    }
                }
            }
        }

        BigDecimal effectivePercentage = adjustedTotal > 0
                ? BigDecimal.valueOf(rawCovered).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(adjustedTotal), 4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP);

        String effectiveStatus = effectivePercentage.compareTo(normalize(gate.threshold())) >= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";

        String effectiveReason = null;
        if (expiredDebtFailed) {
            effectiveStatus = gate.blocking() ? "failed" : "warning";
            effectiveReason = "expired_debt_reappeared";
        } else if (!effectiveStatus.equals(rawStatus)) {
            effectiveReason = "metric_adjusted_by_debt";
        }

        Map<String, Object> details = buildDetailsV2(
                scopeName,
                report.reportId(),
                repositoryContext.contextVersion(),
                rawCovered, rawTotal, rawPercentage, normalize(gate.threshold()), rawStatus,
                debtMode, suppressedFindingIds, suppressedDebtIds, expiredDebtIds, reappearedFindingIds,
                effectivePercentage, effectiveStatus, effectiveReason,
                List.of()
        );

        // Keep top level backward compatibility
        details.put("covered", rawCovered);
        details.put("total", rawTotal);
        details.put("percentage", rawPercentage);
        details.put("threshold", normalize(gate.threshold()));

        return evaluation(report, gate, effectivePercentage, effectiveStatus, details, evaluatedAt);
    }

    private GateEvaluation evaluatePatchCoverageGate(
            CoverageReport report,
            GateConfiguration gate,
            RepositoryContext repositoryContext,
            DiffCoverageReport diffCoverage,
            Instant evaluatedAt) {
        if (diffCoverage == null) {
            return evaluation(report, gate, null, "warning", Map.of(
                    "scope", "patch",
                    "reason", "diff_coverage_missing_or_unavailable",
                    "coverage_report_id", report.reportId().toString()), evaluatedAt);
        }

        Map<String, Object> config = gate.config() != null ? gate.config() : Map.of();
        Map<String, Object> debtConfig = getDebtConfig(config);
        String debtMode = (String) debtConfig.getOrDefault("mode", "none");
        boolean failOnExpiredDebt = getBoolean(debtConfig, "fail_on_expired_debt", true);

        int rawCovered = diffCoverage.patchLineCovered();
        int rawTotal = diffCoverage.patchLineTotal();
        BigDecimal rawPercentage = diffCoverage.patchLinePercentage();
        if (rawPercentage != null) {
            rawPercentage = rawPercentage.setScale(4, RoundingMode.HALF_UP);
        } else {
            rawPercentage = BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP);
        }

        String rawStatus = rawPercentage.compareTo(normalize(gate.threshold())) >= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";

        List<UUID> suppressedFindingIds = new ArrayList<>();
        List<UUID> suppressedDebtIds = new ArrayList<>();
        List<UUID> expiredDebtIds = new ArrayList<>();
        List<UUID> reappearedFindingIds = new ArrayList<>();

        int adjustedTotal = rawTotal;
        boolean expiredDebtFailed = false;

        List<DiffCoverageLine> newlyMissed = diffCoverage.files().stream()
                .flatMap(f -> f.lines().stream())
                .filter(DiffCoverageLine::newlyMissed)
                .toList();

        if ("adjust_metric".equalsIgnoreCase(debtMode)) {
            int waivedLines = 0;
            for (DiffCoverageLine line : newlyMissed) {
                if (line.headLineNumber() == null) continue;
                Finding finding = new Finding(UUID.randomUUID(), line.filePath(), line.headLineNumber(), "low", "active");
                for (DebtItem debtItem : repositoryContext.debtItems()) {
                    if (debtItem.matches(finding) && isActive(debtItem, evaluatedAt)) {
                        waivedLines++;
                        if (!suppressedDebtIds.contains(debtItem.id())) {
                            suppressedDebtIds.add(debtItem.id());
                        }
                        break;
                    }
                }
            }
            adjustedTotal = Math.max(0, rawTotal - waivedLines);
        }

        // Expired debt check on newly missed lines
        for (DiffCoverageLine line : newlyMissed) {
            if (line.headLineNumber() == null) continue;
            Finding finding = new Finding(UUID.randomUUID(), line.filePath(), line.headLineNumber(), "low", "active");
            for (DebtItem debtItem : repositoryContext.debtItems()) {
                if (debtItem.matches(finding) && isExpired(debtItem, evaluatedAt)) {
                    if (!expiredDebtIds.contains(debtItem.id())) {
                        expiredDebtIds.add(debtItem.id());
                    }
                    if (failOnExpiredDebt) {
                        expiredDebtFailed = true;
                    }
                }
            }
        }

        BigDecimal effectivePercentage = adjustedTotal > 0
                ? BigDecimal.valueOf(rawCovered).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(adjustedTotal), 4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP);

        String effectiveStatus = effectivePercentage.compareTo(normalize(gate.threshold())) >= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";

        String effectiveReason = null;
        if (expiredDebtFailed) {
            effectiveStatus = gate.blocking() ? "failed" : "warning";
            effectiveReason = "expired_debt_reappeared";
        } else if (!effectiveStatus.equals(rawStatus)) {
            effectiveReason = "metric_adjusted_by_debt";
        }

        Map<String, Object> details = buildDetailsV2(
                "patch",
                report.reportId(),
                repositoryContext.contextVersion(),
                rawCovered, rawTotal, rawPercentage, normalize(gate.threshold()), rawStatus,
                debtMode, suppressedFindingIds, suppressedDebtIds, expiredDebtIds, reappearedFindingIds,
                effectivePercentage, effectiveStatus, effectiveReason,
                List.of()
        );

        // Keep top level backward compatibility
        details.put("covered", rawCovered);
        details.put("total", rawTotal);
        details.put("percentage", rawPercentage);
        details.put("threshold", normalize(gate.threshold()));

        return evaluation(report, gate, effectivePercentage, effectiveStatus, details, evaluatedAt);
    }

    private GateEvaluation evaluateComponentCoverageGate(
            CoverageReport report,
            GateConfiguration gate,
            RepositoryContext repositoryContext,
            Instant evaluatedAt) {
        // reuse project coverage logic by using component scope
        return evaluateProjectCoverageGate(report, gate, repositoryContext, evaluatedAt);
    }

    private GateEvaluation evaluateCoverageDropGate(
            CoverageReport report,
            GateConfiguration gate,
            RepositoryContext repositoryContext,
            DiffCoverageReport diffCoverage,
            Instant evaluatedAt) {
        BigDecimal headPercentage = percentage(reportMetric(report, gate.metric()).orElse(new CoverageMetric(0, 0)));

        Map<String, Object> config = gate.config() != null ? gate.config() : Map.of();
        Map<String, Object> debtConfig = getDebtConfig(config);
        String debtMode = (String) debtConfig.getOrDefault("mode", "none");
        boolean failOnExpiredDebt = getBoolean(debtConfig, "fail_on_expired_debt", true);

        BigDecimal basePercentage = null;
        if (config.containsKey("base_percentage")) {
            basePercentage = new BigDecimal(config.get("base_percentage").toString());
        } else {
            basePercentage = headPercentage;
        }

        BigDecimal drop = basePercentage.subtract(headPercentage);
        BigDecimal maxDrop = normalize(gate.maxDrop());
        if (maxDrop == null) {
            maxDrop = BigDecimal.ZERO;
        }

        String rawStatus = drop.compareTo(maxDrop) <= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";

        List<UUID> suppressedFindingIds = new ArrayList<>();
        List<UUID> suppressedDebtIds = new ArrayList<>();
        List<UUID> expiredDebtIds = new ArrayList<>();
        List<UUID> reappearedFindingIds = new ArrayList<>();

        // If adjust_metric, adjust headPercentage
        BigDecimal adjustedHeadPercentage = headPercentage;
        if ("adjust_metric".equalsIgnoreCase(debtMode)) {
            CoverageMetric metric = reportMetric(report, gate.metric()).orElse(new CoverageMetric(0, 0));
            int rawTotal = metric.total();
            int rawCovered = metric.covered();
            long waivedLines = report.lineHits().stream()
                    .filter(hit -> hit.hits() == 0)
                    .filter(hit -> {
                        for (DebtItem debtItem : repositoryContext.debtItems()) {
                            if (debtItem.matches(new Finding(UUID.randomUUID(), hit.filePath(), hit.lineNumber(), "low", "active"))) {
                                if (isActive(debtItem, evaluatedAt)) {
                                    if (!suppressedDebtIds.contains(debtItem.id())) {
                                        suppressedDebtIds.add(debtItem.id());
                                    }
                                    return true;
                                }
                            }
                        }
                        return false;
                    })
                    .count();
            int adjustedTotal = Math.max(0, rawTotal - (int) waivedLines);
            adjustedHeadPercentage = adjustedTotal > 0
                    ? BigDecimal.valueOf(rawCovered).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(adjustedTotal), 4, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP);
        }

        // Expired debt check
        boolean expiredDebtFailed = false;
        for (DebtItem debtItem : repositoryContext.debtItems()) {
            if (isExpired(debtItem, evaluatedAt)) {
                expiredDebtIds.add(debtItem.id());
                if (failOnExpiredDebt) {
                    expiredDebtFailed = true;
                }
            }
        }

        BigDecimal adjustedDrop = basePercentage.subtract(adjustedHeadPercentage);
        String effectiveStatus = adjustedDrop.compareTo(maxDrop) <= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";

        String effectiveReason = null;
        if (expiredDebtFailed) {
            effectiveStatus = gate.blocking() ? "failed" : "warning";
            effectiveReason = "expired_debt_reappeared";
        } else if (!effectiveStatus.equals(rawStatus)) {
            effectiveReason = "metric_adjusted_by_debt";
        }

        Map<String, Object> details = buildDetailsV2(
                "coverage_drop",
                report.reportId(),
                repositoryContext.contextVersion(),
                headPercentage.intValue(), basePercentage.intValue(), headPercentage, maxDrop, rawStatus,
                debtMode, suppressedFindingIds, suppressedDebtIds, expiredDebtIds, reappearedFindingIds,
                adjustedHeadPercentage, effectiveStatus, effectiveReason,
                List.of()
        );

        // Keep top level backward compatibility
        details.put("covered", headPercentage.intValue());
        details.put("total", basePercentage.intValue());
        details.put("percentage", headPercentage);
        details.put("threshold", maxDrop);

        return evaluation(report, gate, adjustedHeadPercentage, effectiveStatus, details, evaluatedAt);
    }

    private static Map<String, Object> buildDetailsV2(
            String scope,
            java.util.UUID reportId,
            String contextVersion,
            int rawCovered, int rawTotal, BigDecimal rawPercentage, BigDecimal threshold, String rawStatus,
            String debtMode, List<UUID> suppressedFindings, List<UUID> suppressedDebts, List<UUID> expiredDebts, List<UUID> reappearedFindings,
            BigDecimal effectiveActual, String effectiveStatus, String effectiveReason,
            List<UUID> topFindings) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("schema_version", 2);
        details.put("scope", scope);
        details.put("coverage_report_id", reportId.toString());
        details.put("context_version", contextVersion);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("covered", rawCovered);
        raw.put("total", rawTotal);
        raw.put("percentage", rawPercentage);
        raw.put("threshold", threshold);
        raw.put("status", rawStatus);
        details.put("raw", raw);

        Map<String, Object> debt = new LinkedHashMap<>();
        debt.put("mode", debtMode);
        debt.put("suppressed_finding_ids", toStringList(suppressedFindings));
        debt.put("suppressed_debt_item_ids", toStringList(suppressedDebts));
        debt.put("expired_debt_item_ids", toStringList(expiredDebts));
        debt.put("reappeared_finding_ids", toStringList(reappearedFindings));
        details.put("debt", debt);

        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("actual", effectiveActual);
        effective.put("status", effectiveStatus);
        effective.put("reason", effectiveReason);
        details.put("effective", effective);

        details.put("top_findings", toStringList(topFindings));

        return details;
    }

    private static List<String> toStringList(List<UUID> uuids) {
        if (uuids == null) return List.of();
        return uuids.stream().map(UUID::toString).toList();
    }

    private static Map<String, Object> getDebtConfig(Map<String, Object> config) {
        Object val = config.get("debt");
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return Map.of();
    }

    private static List<String> getList(Map<String, Object> map, String key, List<String> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private static boolean isExpired(DebtItem debtItem, Instant evaluatedAt) {
        if ("expired".equalsIgnoreCase(debtItem.status())) {
            return true;
        }
        return debtItem.expiresAt() != null && debtItem.expiresAt().isBefore(evaluatedAt);
    }

    private boolean isActive(DebtItem debtItem, Instant evaluatedAt) {
        if (!"active".equalsIgnoreCase(debtItem.status())) {
            return false;
        }
        return debtItem.expiresAt() == null || debtItem.expiresAt().isAfter(evaluatedAt);
    }

    private static CoverageMetric getFileMetric(CoverageFileSummary file, String metric) {
        return switch (metric) {
            case "line" -> file.line();
            case "branch" -> file.branch();
            case "function" -> file.function();
            case "statement" -> file.statement();
            default -> new CoverageMetric(0, 0);
        };
    }

    private static CoverageMetric getRollupMetric(ComponentRollup rollup, String metric) {
        return switch (metric) {
            case "line" -> rollup.line();
            case "branch" -> rollup.branch();
            case "function" -> rollup.function();
            case "statement" -> rollup.statement();
            default -> new CoverageMetric(0, 0);
        };
    }

    private static GateEvaluation evaluation(
            CoverageReport report,
            GateConfiguration gate,
            BigDecimal actual,
            String status,
            Map<String, Object> details,
            Instant evaluatedAt) {
        return new GateEvaluation(
                UUID.randomUUID(),
                report.tenantId(),
                report.repositoryId(),
                report.reportId(),
                report.commitSha(),
                report.branchName(),
                report.pullRequestNumber(),
                gate.name(),
                gate.gateType(),
                gate.metric(),
                normalize(gate.threshold()),
                actual,
                status,
                gate.blocking(),
                details,
                evaluatedAt);
    }

    private static Optional<CoverageMetric> reportMetric(CoverageReport report, String metric) {
        return switch (metric) {
            case "line" -> Optional.of(report.line());
            case "branch" -> Optional.of(report.branch());
            case "function" -> Optional.of(report.function());
            case "statement" -> Optional.of(report.statement());
            default -> Optional.empty();
        };
    }

    private static BigDecimal percentage(CoverageMetric metric) {
        return BigDecimal.valueOf(metric.percentage()).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }
}
