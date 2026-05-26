package dev.vericov.analysis.gaps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CoverageRiskScorer {

    public CoverageRiskScore score(CoverageGapCandidate candidate) {
        List<RiskFactorContribution> factors = new ArrayList<>();
        factors.add(changeExposure(candidate));
        factors.add(componentCriticality(candidate));
        factors.add(coverageSeverity(candidate));
        factors.add(ownershipSignal(candidate));
        factors.add(blastRadius(candidate));
        factors.add(factor("historical_trend", 0, "no_trend_input"));
        factors.add(debtState(candidate));
        factors.add(policyOverride(candidate));

        BigDecimal total = factors.stream()
                .map(RiskFactorContribution::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal normalized = normalizeScore(total);
        return new CoverageRiskScore(normalized, levelFor(normalized), factors);
    }

    public BigDecimal normalizeScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        if (score.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100).setScale(1, RoundingMode.HALF_UP);
        }
        return score.setScale(1, RoundingMode.HALF_UP);
    }

    public String levelFor(BigDecimal score) {
        BigDecimal normalized = normalizeScore(score);
        if (normalized.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return "critical";
        }
        if (normalized.compareTo(BigDecimal.valueOf(65)) >= 0) {
            return "high";
        }
        if (normalized.compareTo(BigDecimal.valueOf(35)) >= 0) {
            return "medium";
        }
        return "low";
    }

    private static RiskFactorContribution changeExposure(CoverageGapCandidate candidate) {
        return switch (candidate.reasonCode()) {
            case "new_uncovered_changed_line" -> factor("change_exposure", 25, "new_uncovered_changed_line");
            case "lost_existing_coverage", "expired_debt_reappeared" ->
                    factor("change_exposure", 22, "lost_existing_coverage");
            case "uncovered_executable_line" -> factor("change_exposure", 10, "existing_uncovered_line");
            case "file_has_no_executable_coverage" -> factor("change_exposure", 8, "existing_zero_covered_file");
            default -> factor("change_exposure", 0, "no_change_exposure");
        };
    }

    private static RiskFactorContribution componentCriticality(CoverageGapCandidate candidate) {
        String criticality = policyCriticalityOverride(candidate);
        if (criticality == null) {
            criticality = candidate.componentCriticality() == null ? "medium" : candidate.componentCriticality();
        }
        return switch (criticality) {
            case "critical" -> factor("component_criticality", 20, "component_critical");
            case "high" -> factor("component_criticality", 14, "component_high");
            case "low" -> factor("component_criticality", 3, "component_low");
            default -> factor("component_criticality", 8, "component_medium");
        };
    }

    private static RiskFactorContribution coverageSeverity(CoverageGapCandidate candidate) {
        return switch (candidate.reasonCode()) {
            case "new_uncovered_changed_line" -> factor("coverage_severity", 15, "zero_hit_changed_line");
            case "lost_existing_coverage", "expired_debt_reappeared" ->
                    factor("coverage_severity", 12, "coverage_regressed_to_zero");
            case "file_has_no_executable_coverage" -> factor("coverage_severity", 12, "zero_covered_file");
            case "uncovered_executable_line" -> factor("coverage_severity", 8, "zero_hit_executable_line");
            default -> factor("coverage_severity", 0, "no_coverage_severity");
        };
    }

    private static RiskFactorContribution ownershipSignal(CoverageGapCandidate candidate) {
        if (candidate.owners().isEmpty() || candidate.owners().contains("unowned")) {
            return factor("ownership_signal", 4, "unowned_code");
        }
        return factor("ownership_signal", 8, "owned_code");
    }

    private static RiskFactorContribution blastRadius(CoverageGapCandidate candidate) {
        if (candidate.packageNode() == null || candidate.packageNode().metadata().isEmpty()) {
            return factor("blast_radius", 0, "no_package_graph_signal");
        }
        Map<String, Object> metadata = candidate.packageNode().metadata();
        if (Boolean.TRUE.equals(metadata.get("shared_library"))) {
            return factor("blast_radius", 10, "shared_library_package");
        }
        int dependents = intValue(metadata.get("dependent_count"));
        if (dependents == 0 && metadata.get("dependents") instanceof List<?> list) {
            dependents = list.size();
        }
        if (dependents >= 10) {
            return factor("blast_radius", 10, "many_dependents");
        }
        if (dependents >= 3) {
            return factor("blast_radius", 6, "several_dependents");
        }
        if (dependents > 0) {
            return factor("blast_radius", 3, "direct_dependents");
        }
        return factor("blast_radius", 0, "no_dependents");
    }

    private static RiskFactorContribution debtState(CoverageGapCandidate candidate) {
        if (candidate.debtSuppressed()) {
            return factor("debt_state", -20, "active_matching_debt");
        }
        if (candidate.expiredDebtMatched()) {
            return factor("debt_state", 15, "expired_matching_debt");
        }
        return factor("debt_state", 0, "no_matching_debt");
    }

    private static RiskFactorContribution policyOverride(CoverageGapCandidate candidate) {
        BigDecimal total = BigDecimal.ZERO;
        String reason = "no_policy_override";
        Object overrides = candidate.riskConfig().get("path_overrides");
        if (overrides instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> override)) {
                    continue;
                }
                Object pattern = override.get("pattern");
                if (pattern instanceof String stringPattern && pathMatches(stringPattern, candidate.filePath())) {
                    BigDecimal boost = decimalValue(override.get("score_boost"));
                    BigDecimal dampening = decimalValue(override.get("score_dampening"));
                    total = total.add(boost).subtract(dampening);
                    reason = "path_policy_override";
                }
            }
        }
        if (total.compareTo(BigDecimal.valueOf(50)) > 0) {
            total = BigDecimal.valueOf(50);
        }
        if (total.compareTo(BigDecimal.valueOf(-50)) < 0) {
            total = BigDecimal.valueOf(-50);
        }
        return new RiskFactorContribution("policy_override", total.setScale(1, RoundingMode.HALF_UP), reason);
    }

    private static String policyCriticalityOverride(CoverageGapCandidate candidate) {
        Object pathOverrides = candidate.riskConfig().get("path_overrides");
        if (pathOverrides instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> override
                        && override.get("pattern") instanceof String pattern
                        && pathMatches(pattern, candidate.filePath())
                        && override.get("criticality") instanceof String criticality) {
                    return criticality;
                }
            }
        }
        Object componentOverrides = candidate.riskConfig().get("component_overrides");
        if (componentOverrides instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> override) || !(override.get("criticality") instanceof String criticality)) {
                    continue;
                }
                Object component = override.get("component");
                if (componentMatches(candidate, component)) {
                    return criticality;
                }
            }
        }
        return null;
    }

    private static boolean componentMatches(CoverageGapCandidate candidate, Object component) {
        if (!(component instanceof String expected) || expected.isBlank()) {
            return false;
        }
        return expected.equals(candidate.componentName())
                || (candidate.componentId() != null && expected.equals(candidate.componentId().toString()));
    }

    private static RiskFactorContribution factor(String name, int value, String reason) {
        return new RiskFactorContribution(
                name,
                BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP),
                reason);
    }

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String string && !string.isBlank()) {
            return new BigDecimal(string);
        }
        return BigDecimal.ZERO;
    }

    private static int intValue(Object value) {
        return decimalValue(value).setScale(0, RoundingMode.DOWN).intValue();
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
}
