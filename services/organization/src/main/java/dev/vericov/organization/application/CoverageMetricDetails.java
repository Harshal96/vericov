package dev.vericov.organization.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CoverageMetricDetails(
        int covered,
        int total,
        BigDecimal percent) {

    public static CoverageMetricDetails of(int covered, int total) {
        BigDecimal percent = total == 0
                ? BigDecimal.ZERO
                : new BigDecimal(covered)
                        .multiply(new BigDecimal("100"))
                        .divide(new BigDecimal(total), 1, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
        return new CoverageMetricDetails(covered, total, percent);
    }
}
