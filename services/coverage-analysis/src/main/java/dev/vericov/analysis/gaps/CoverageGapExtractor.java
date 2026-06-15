package dev.vericov.analysis.gaps;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffCoverageFile;
import dev.vericov.analysis.diff.DiffCoverageLine;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.gates.DebtItem;
import dev.vericov.analysis.gates.RepositoryComponentContext;
import dev.vericov.analysis.gates.RepositoryContext;
import dev.vericov.analysis.gates.RepositoryPackageNodeContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CoverageGapExtractor {
    private final CoverageRiskScorer scorer = new CoverageRiskScorer();

    public List<CoverageGapFinding> extract(
            CoverageReport report,
            RepositoryContext context,
            DiffCoverageReport diffCoverage,
            Instant now) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(now, "now");
        List<CoverageGapFinding> findings = new ArrayList<>();
        Set<String> diffSpecificLines = collectDiffSpecificLines(diffCoverage);
        Set<String> reportPaths = reportPaths(report);

        if (diffCoverage != null) {
            for (DiffCoverageFile file : diffCoverage.files()) {
                if ("base_coverage_missing".equals(diffCoverage.status())) {
                    findings.add(finding(
                            report,
                            context,
                            "base_coverage_missing",
                            file.filePath(),
                            "file",
                            null,
                            null,
                            null,
                            diffEvidence(diffCoverage),
                            now));
                }
                if (!reportPaths.contains(file.filePath())) {
                    String reasonCode = pathReasonCode(context, file.filePath(), reportPaths);
                    findings.add(finding(
                            report,
                            context,
                            reasonCode,
                            file.filePath(),
                            "file",
                            null,
                            null,
                            null,
                            pathEvidence(diffCoverage, file.filePath(), reportPaths),
                            now));
                }
                for (DiffCoverageLine line : file.lines()) {
                    if (line.headLineNumber() == null) {
                        continue;
                    }
                    if (line.newlyMissed()) {
                        findings.add(finding(
                                report,
                                context,
                                "new_uncovered_changed_line",
                                file.filePath(),
                                "line",
                                line.headLineNumber(),
                                line.headLineNumber(),
                                null,
                                Map.of(
                                        "base_sha", diffCoverage.baseSha(),
                                        "head_sha", diffCoverage.headSha(),
                                        "head_hits", line.headHits() == null ? 0L : line.headHits()),
                                now));
                    } else if (line.lostCoverage()) {
                        findings.add(finding(
                                report,
                                context,
                                "lost_existing_coverage",
                                file.filePath(),
                                "line",
                                line.headLineNumber(),
                                line.headLineNumber(),
                                null,
                                Map.of(
                                        "base_sha", diffCoverage.baseSha(),
                                        "head_sha", diffCoverage.headSha(),
                                        "base_hits", line.baseHits() == null ? 0L : line.baseHits(),
                                        "head_hits", line.headHits() == null ? 0L : line.headHits()),
                                now));
                    }
                }
            }
        }

        for (CoverageLineHit lineHit : report.lineHits()) {
            if (lineHit.hits() > 0 || diffSpecificLines.contains(lineKey(lineHit.filePath(), lineHit.lineNumber()))) {
                continue;
            }
            findings.add(finding(
                    report,
                    context,
                    "uncovered_executable_line",
                    lineHit.filePath(),
                    "line",
                    lineHit.lineNumber(),
                    lineHit.lineNumber(),
                    null,
                    Map.of("head_hits", lineHit.hits()),
                    now));
        }

        for (CoverageFileSummary file : report.files()) {
            if (file.line().total() > 0 && file.line().covered() == 0) {
                findings.add(finding(
                        report,
                        context,
                        "file_has_no_executable_coverage",
                        file.filePath(),
                        "file",
                        null,
                        null,
                        null,
                        Map.of(
                                "line_covered", file.line().covered(),
                                "line_total", file.line().total()),
                        now));
            }
        }

        return groupAdjacentLineFindings(findings);
    }

    private CoverageGapFinding finding(
            CoverageReport report,
            RepositoryContext context,
            String initialReasonCode,
            String filePath,
            String targetType,
            Integer lineStart,
            Integer lineEnd,
            String symbolName,
            Map<String, Object> evidence,
            Instant now) {
        FileContext fileContext = resolveFileContext(report, context, filePath);
        List<DebtItem> activeDebt = matchingDebt(context, filePath, lineStart, now, true);
        List<DebtItem> expiredDebt = matchingDebt(context, filePath, lineStart, now, false);
        String policyReasonCode = matchesGeneratedOrIgnoredPath(context, filePath)
                ? "generated_or_ignored_candidate"
                : initialReasonCode;
        String reasonCode = expiredDebt.isEmpty() ? policyReasonCode : "expired_debt_reappeared";
        String status = activeDebt.isEmpty() ? "active" : "debt_suppressed";
        CoverageGapCandidate candidate = new CoverageGapCandidate(
                filePath,
                targetType,
                lineStart,
                lineEnd,
                symbolName,
                reasonCode,
                fileContext.componentKey(),
                fileContext.componentName(),
                fileContext.criticality(),
                fileContext.owners(),
                fileContext.packageNode(),
                !activeDebt.isEmpty(),
                !expiredDebt.isEmpty(),
                context.riskConfig());
        CoverageRiskScore score = scorer.score(candidate);

        Map<String, Object> evidenceJson = new LinkedHashMap<>();
        evidenceJson.put("schema_version", 1);
        evidenceJson.put("coverage_report_id", report.reportId().toString());
        evidenceJson.put("context_version", context.contextVersion());
        evidenceJson.put("component_key", fileContext.componentKey());
        evidenceJson.putAll(evidence);
        if (!activeDebt.isEmpty()) {
            evidenceJson.put("debt_item_ids", activeDebt.stream().map(item -> item.id().toString()).toList());
        }
        if (!expiredDebt.isEmpty()) {
            evidenceJson.put("expired_debt_item_ids", expiredDebt.stream().map(item -> item.id().toString()).toList());
        }
        evidenceJson.put("score", score.toEvidence());

        return new CoverageGapFinding(
                stableId(report, filePath, targetType, lineStart, lineEnd, reasonCode),
                report.tenantId(),
                report.repositoryId(),
                report.reportId(),
                fileContext.componentKey(),
                report.commitSha(),
                report.pullRequestNumber(),
                filePath,
                targetType,
                lineStart,
                lineEnd,
                symbolName,
                reasonCode,
                explanation(reasonCode, filePath, lineStart, activeDebt, expiredDebt),
                confidence(reasonCode),
                score.total(),
                score.level(),
                fileContext.owners(),
                nextAction(score.level(), reasonCode, status),
                status,
                evidenceJson,
                now,
                now);
    }

    private static FileContext resolveFileContext(
            CoverageReport report,
            RepositoryContext context,
            String filePath) {
        CoverageFileSummary file = report.files().stream()
                .filter(summary -> summary.filePath().equals(filePath))
                .findFirst()
                .orElse(null);
        RepositoryComponentContext component = null;
        if (file != null && file.componentId() != null) {
            component = context.components().stream()
                    .filter(candidate -> candidate.componentId().equals(file.componentId()))
                    .findFirst()
                    .orElse(null);
        }
        if (component == null) {
            component = context.components().stream()
                    .filter(candidate -> candidate.pathPatterns().stream()
                            .anyMatch(pattern -> pathMatches(pattern, filePath)))
                    .findFirst()
                    .orElse(null);
        }
        RepositoryPackageNodeContext packageNode = context.packageNodes().stream()
                .filter(node -> filePath.equals(node.packagePath()) || filePath.startsWith(node.packagePath() + "/"))
                .max(java.util.Comparator.comparingInt(node -> node.packagePath().length()))
                .orElse(null);
        List<String> owners = component != null
                ? component.owners()
                : file == null || file.owners().isEmpty() ? List.of("unowned") : file.owners();
        if (owners.isEmpty()) {
            owners = List.of("unowned");
        }
        String componentKey = component != null
                ? component.componentId().toString()
                : file == null ? null : file.leafComponentKey();
        return new FileContext(
                componentKey,
                component == null ? null : component.name(),
                component == null ? "medium" : component.criticality(),
                owners,
                packageNode);
    }

    private static List<DebtItem> matchingDebt(
            RepositoryContext context,
            String filePath,
            Integer line,
            Instant now,
            boolean active) {
        dev.vericov.analysis.gates.Finding finding = new dev.vericov.analysis.gates.Finding(
                UUID.randomUUID(),
                filePath,
                line,
                "low",
                "active");
        return context.debtItems().stream()
                .filter(item -> item.matches(finding))
                .filter(item -> active == isActive(item, now))
                .toList();
    }

    private static boolean isActive(DebtItem item, Instant now) {
        return "active".equalsIgnoreCase(item.status())
                && (item.expiresAt() == null || item.expiresAt().isAfter(now));
    }

    private static Set<String> collectDiffSpecificLines(DiffCoverageReport diffCoverage) {
        if (diffCoverage == null) {
            return Set.of();
        }
        return diffCoverage.files().stream()
                .flatMap(file -> file.lines().stream())
                .filter(line -> line.headLineNumber() != null)
                .filter(line -> line.newlyMissed() || line.lostCoverage())
                .map(line -> lineKey(line.filePath(), line.headLineNumber()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> reportPaths(CoverageReport report) {
        Set<String> filePaths = report.files().stream()
                .map(CoverageFileSummary::filePath)
                .collect(Collectors.toSet());
        report.lineHits().stream()
                .map(CoverageLineHit::filePath)
                .forEach(filePaths::add);
        return Set.copyOf(filePaths);
    }

    private static String pathReasonCode(
            RepositoryContext context,
            String filePath,
            Set<String> reportPaths) {
        if (matchesAnyConfiguredPath(context.riskConfig(), filePath, "generated_path_patterns")
                || matchesAnyConfiguredPath(context.riskConfig(), filePath, "ignored_path_patterns")) {
            return "generated_or_ignored_candidate";
        }
        return hasPathMismatchHint(filePath, reportPaths) ? "possible_path_mismatch" : "path_not_in_report";
    }

    private static boolean matchesAnyConfiguredPath(
            Map<String, Object> config,
            String filePath,
            String key) {
        Object patterns = config.get(key);
        if (!(patterns instanceof List<?> list)) {
            return false;
        }
        for (Object pattern : list) {
            if (pattern instanceof String stringPattern && pathMatches(stringPattern, filePath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGeneratedOrIgnoredPath(RepositoryContext context, String filePath) {
        return matchesAnyConfiguredPath(context.riskConfig(), filePath, "generated_path_patterns")
                || matchesAnyConfiguredPath(context.riskConfig(), filePath, "ignored_path_patterns");
    }

    private static boolean hasPathMismatchHint(String diffPath, Set<String> reportPaths) {
        String normalizedDiff = normalizePath(diffPath);
        String basename = basename(normalizedDiff);
        for (String reportPath : reportPaths) {
            String normalizedReport = normalizePath(reportPath);
            if (basename.equals(basename(normalizedReport))
                    || normalizedReport.endsWith("/" + normalizedDiff)
                    || normalizedDiff.endsWith("/" + normalizedReport)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePath(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static Map<String, Object> diffEvidence(DiffCoverageReport diffCoverage) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("base_sha", diffCoverage.baseSha());
        evidence.put("head_sha", diffCoverage.headSha());
        evidence.put("diff_status", diffCoverage.status());
        return Map.copyOf(evidence);
    }

    private static Map<String, Object> pathEvidence(
            DiffCoverageReport diffCoverage,
            String filePath,
            Set<String> reportPaths) {
        Map<String, Object> evidence = new LinkedHashMap<>(diffEvidence(diffCoverage));
        evidence.put("diff_file_path", filePath);
        List<String> hints = reportPaths.stream()
                .filter(reportPath -> hasPathMismatchHint(filePath, Set.of(reportPath)))
                .sorted()
                .limit(5)
                .toList();
        if (!hints.isEmpty()) {
            evidence.put("path_match_hints", hints);
        }
        return Map.copyOf(evidence);
    }

    private static String lineKey(String filePath, int lineNumber) {
        return filePath + ":" + lineNumber;
    }

    private static List<CoverageGapFinding> groupAdjacentLineFindings(List<CoverageGapFinding> findings) {
        List<CoverageGapFinding> grouped = new ArrayList<>();
        List<CoverageGapFinding> uncoveredLines = findings.stream()
                .filter(CoverageGapExtractor::groupableUncoveredLine)
                .sorted(java.util.Comparator
                        .comparing(CoverageGapFinding::filePath)
                        .thenComparing(CoverageGapFinding::lineStart))
                .toList();
        Set<UUID> groupedIds = uncoveredLines.stream()
                .map(CoverageGapFinding::id)
                .collect(Collectors.toSet());

        int index = 0;
        while (index < uncoveredLines.size()) {
            List<CoverageGapFinding> run = new ArrayList<>();
            CoverageGapFinding first = uncoveredLines.get(index);
            run.add(first);
            int next = index + 1;
            while (next < uncoveredLines.size() && canGroup(run.getLast(), uncoveredLines.get(next))) {
                run.add(uncoveredLines.get(next));
                next++;
            }
            grouped.add(run.size() == 1 ? first : rangeFinding(run));
            index = next;
        }

        for (CoverageGapFinding finding : findings) {
            if (!groupedIds.contains(finding.id())) {
                grouped.add(finding);
            }
        }

        return grouped.stream()
                .sorted(java.util.Comparator
                        .comparing(CoverageGapFinding::filePath)
                        .thenComparing(finding -> finding.lineStart() == null ? Integer.MAX_VALUE : finding.lineStart())
                        .thenComparing(CoverageGapFinding::reasonCode))
                .toList();
    }

    private static boolean groupableUncoveredLine(CoverageGapFinding finding) {
        return "uncovered_executable_line".equals(finding.reasonCode())
                && "line".equals(finding.targetType())
                && finding.lineStart() != null
                && finding.lineEnd() != null
                && finding.lineStart().equals(finding.lineEnd());
    }

    private static boolean canGroup(CoverageGapFinding previous, CoverageGapFinding next) {
        return Objects.equals(previous.filePath(), next.filePath())
                && Objects.equals(previous.reasonCode(), next.reasonCode())
                && Objects.equals(previous.componentKey(), next.componentKey())
                && Objects.equals(previous.owners(), next.owners())
                && Objects.equals(previous.nextAction(), next.nextAction())
                && Objects.equals(previous.status(), next.status())
                && previous.lineEnd() != null
                && next.lineStart() != null
                && next.lineStart() == previous.lineEnd() + 1;
    }

    private static CoverageGapFinding rangeFinding(List<CoverageGapFinding> run) {
        CoverageGapFinding first = run.getFirst();
        CoverageGapFinding last = run.getLast();
        Map<String, Object> evidence = new LinkedHashMap<>(first.evidence());
        evidence.put("line_start", first.lineStart());
        evidence.put("line_end", last.lineEnd());
        evidence.put("line_count", run.size());
        String explanation = "Lines " + first.lineStart() + "-" + last.lineEnd()
                + " are uncovered in the coverage report.";
        return new CoverageGapFinding(
                stableId(
                        first.repositoryId(),
                        first.coverageReportId(),
                        first.filePath(),
                        "range",
                        first.lineStart(),
                        last.lineEnd(),
                        first.reasonCode()),
                first.tenantId(),
                first.repositoryId(),
                first.coverageReportId(),
                first.componentKey(),
                first.commitSha(),
                first.pullRequestNumber(),
                first.filePath(),
                "range",
                first.lineStart(),
                last.lineEnd(),
                first.symbolName(),
                first.reasonCode(),
                explanation,
                first.confidence(),
                first.riskScore(),
                first.riskLevel(),
                first.owners(),
                first.nextAction(),
                first.status(),
                evidence,
                first.createdAt(),
                last.updatedAt());
    }

    private static UUID stableId(
            CoverageReport report,
            String filePath,
            String targetType,
            Integer lineStart,
            Integer lineEnd,
            String reasonCode) {
        return stableId(report.repositoryId(), report.reportId(), filePath, targetType, lineStart, lineEnd, reasonCode);
    }

    private static UUID stableId(
            UUID repositoryId,
            UUID coverageReportId,
            String filePath,
            String targetType,
            Integer lineStart,
            Integer lineEnd,
            String reasonCode) {
        String identity = repositoryId
                + ":" + coverageReportId
                + ":" + filePath
                + ":" + targetType
                + ":" + lineStart
                + ":" + lineEnd
                + ":" + reasonCode;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean pathMatches(String pattern, String filePath) {
        if (pattern == null || filePath == null) {
            return false;
        }
        return Pattern.compile(globRegex(pattern)).matcher(filePath).matches();
    }

    private static String globRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        if (!pattern.contains("/")) {
            regex.append("(?:.*/)?");
        }
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '*') {
                boolean doublestar = i + 1 < pattern.length() && pattern.charAt(i + 1) == '*';
                if (doublestar) {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else {
                if (".[]{}()+-^$|".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        return regex.append('$').toString();
    }

    private static String confidence(String reasonCode) {
        return switch (reasonCode) {
            case "base_coverage_missing", "file_has_no_executable_coverage", "path_not_in_report",
                    "possible_path_mismatch" -> "medium";
            case "generated_or_ignored_candidate" -> "low";
            default -> "high";
        };
    }

    private static String explanation(
            String reasonCode,
            String filePath,
            Integer line,
            List<DebtItem> activeDebt,
            List<DebtItem> expiredDebt) {
        if (!activeDebt.isEmpty()) {
            return "Matching active debt suppresses this gap.";
        }
        if (!expiredDebt.isEmpty()) {
            return "Matching expired debt no longer suppresses this gap.";
        }
        return switch (reasonCode) {
            case "new_uncovered_changed_line" -> "Added executable line " + line + " is uncovered in the head report.";
            case "lost_existing_coverage" -> "Line " + line + " was covered in the base report but has zero hits in the head report.";
            case "file_has_no_executable_coverage" -> "File " + filePath + " has executable lines but no covered lines.";
            case "base_coverage_missing" -> "Base coverage is unavailable for this pull request, so lost coverage cannot be fully classified.";
            case "path_not_in_report" -> "This changed file has provider diff lines but no matching coverage records.";
            case "possible_path_mismatch" -> "This changed file may have a coverage path mismatch; inspect path normalization or instrumentation.";
            case "generated_or_ignored_candidate" -> "This changed path may match generated or ignored coverage policy but is not explicitly suppressed.";
            default -> "Executable line " + line + " is uncovered in the coverage report.";
        };
    }

    private static String nextAction(String level, String reasonCode, String status) {
        if ("debt_suppressed".equals(status)) {
            return "create_debt";
        }
        if ("generated_or_ignored_candidate".equals(reasonCode)) {
            return "mark_generated";
        }
        if ("path_not_in_report".equals(reasonCode) || "possible_path_mismatch".equals(reasonCode)) {
            return "inspect_instrumentation";
        }
        if ("base_coverage_missing".equals(reasonCode)) {
            return "run_source_explain";
        }
        if ("critical".equals(level) || "high".equals(level)) {
            return "add_test";
        }
        if ("medium".equals(level)
                && ("new_uncovered_changed_line".equals(reasonCode) || "lost_existing_coverage".equals(reasonCode))) {
            return "add_test";
        }
        return "create_debt";
    }

    private record FileContext(
            String componentKey,
            String componentName,
            String criticality,
            List<String> owners,
            RepositoryPackageNodeContext packageNode) {

        private FileContext {
            owners = List.copyOf(owners == null ? List.of() : owners);
        }
    }
}
