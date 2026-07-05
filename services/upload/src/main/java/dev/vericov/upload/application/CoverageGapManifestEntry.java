package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CoverageGapManifestEntry(
        UUID findingId,
        int rank,
        String filePath,
        String targetType,
        Integer lineStart,
        Integer lineEnd,
        String symbolName,
        boolean inPatch,
        String reasonCode,
        String explanation,
        String confidence,
        BigDecimal riskScore,
        String riskLevel,
        List<String> riskFactors,
        String componentKey,
        List<String> owners,
        String nextAction,
        List<CoverageLineRange> uncoveredRanges,
        boolean rangesTruncated) {

    public CoverageGapManifestEntry {
        riskFactors = List.copyOf(riskFactors == null ? List.of() : riskFactors);
        owners = List.copyOf(owners == null ? List.of() : owners);
        uncoveredRanges = List.copyOf(uncoveredRanges == null ? List.of() : uncoveredRanges);
    }
}
