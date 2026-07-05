package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.util.List;

public record CoverageGapFindingDetails(
        String filePath,
        String targetType,
        Integer lineStart,
        Integer lineEnd,
        String symbolName,
        String reasonCode,
        String explanation,
        String confidence,
        BigDecimal riskScore,
        String riskLevel,
        List<String> owners,
        String componentKey,
        String nextAction,
        String status) {

    public CoverageGapFindingDetails {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
