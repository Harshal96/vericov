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

        if (diffCoverage != null) {
            for (DiffCoverageFile file : diffCoverage.files()) {
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

        return List.copyOf(findings);
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
        String reasonCode = expiredDebt.isEmpty() ? initialReasonCode : "expired_debt_reappeared";
        String status = activeDebt.isEmpty() ? "active" : "debt_suppressed";
        CoverageGapCandidate candidate = new CoverageGapCandidate(
                filePath,
                targetType,
                lineStart,
                lineEnd,
                symbolName,
                reasonCode,
                fileContext.componentId(),
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
        evidenceJson.put("component_id", fileContext.componentId() == null ? null : fileContext.componentId().toString());
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
                fileContext.componentId(),
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
        UUID componentId = component != null ? component.componentId() : file == null ? null : file.componentId();
        return new FileContext(
                componentId,
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

    private static String lineKey(String filePath, int lineNumber) {
        return filePath + ":" + lineNumber;
    }

    private static UUID stableId(
            CoverageReport report,
            String filePath,
            String targetType,
            Integer lineStart,
            Integer lineEnd,
            String reasonCode) {
        String identity = report.repositoryId()
                + ":" + report.reportId()
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
            case "file_has_no_executable_coverage" -> "medium";
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
            default -> "Executable line " + line + " is uncovered in the coverage report.";
        };
    }

    private static String nextAction(String level, String reasonCode, String status) {
        if ("debt_suppressed".equals(status)) {
            return "create_debt";
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
            UUID componentId,
            String componentName,
            String criticality,
            List<String> owners,
            RepositoryPackageNodeContext packageNode) {

        private FileContext {
            owners = List.copyOf(owners == null ? List.of() : owners);
        }
    }
}
