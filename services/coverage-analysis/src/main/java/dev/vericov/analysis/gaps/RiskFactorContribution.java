package dev.vericov.analysis.gaps;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public record RiskFactorContribution(
        String name,
        BigDecimal value,
        String reason) {

    public RiskFactorContribution {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(reason, "reason");
    }

    public Map<String, Object> toEvidence() {
        return Map.of(
                "name", name,
                "value", value,
                "reason", reason);
    }
}
