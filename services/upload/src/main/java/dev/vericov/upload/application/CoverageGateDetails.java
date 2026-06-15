package dev.vericov.upload.application;

import java.math.BigDecimal;

public record CoverageGateDetails(
        String metric,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking) {
}
