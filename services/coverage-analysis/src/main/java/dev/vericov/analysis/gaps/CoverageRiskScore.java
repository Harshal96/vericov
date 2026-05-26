package dev.vericov.analysis.gaps;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CoverageRiskScore(
        BigDecimal total,
        String level,
        List<RiskFactorContribution> factors) {

    public CoverageRiskScore {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(level, "level");
        factors = List.copyOf(factors == null ? List.of() : factors);
    }

    public Map<String, Object> toEvidence() {
        return Map.of(
                "schema_version", 1,
                "total", total,
                "level", level,
                "factors", factors.stream().map(RiskFactorContribution::toEvidence).toList());
    }
}
