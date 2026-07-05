package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageGapManifestEntry;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CoverageGapManifestEntryHttpResponse(
        @JsonbProperty("finding_id") UUID findingId,
        int rank,
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("target_type") String targetType,
        @JsonbProperty("line_start") Integer lineStart,
        @JsonbProperty("line_end") Integer lineEnd,
        @JsonbProperty("symbol_name") String symbolName,
        @JsonbProperty("in_patch") boolean inPatch,
        @JsonbProperty("reason_code") String reasonCode,
        String explanation,
        String confidence,
        RiskHttpResponse risk,
        @JsonbProperty("component_key") String componentKey,
        List<String> owners,
        @JsonbProperty("next_action") String nextAction,
        @JsonbProperty("uncovered_ranges") List<CoverageLineRangeHttpResponse> uncoveredRanges,
        @JsonbProperty("ranges_truncated") boolean rangesTruncated) {

    public static CoverageGapManifestEntryHttpResponse from(CoverageGapManifestEntry entry) {
        return new CoverageGapManifestEntryHttpResponse(
                entry.findingId(),
                entry.rank(),
                entry.filePath(),
                entry.targetType(),
                entry.lineStart(),
                entry.lineEnd(),
                entry.symbolName(),
                entry.inPatch(),
                entry.reasonCode(),
                entry.explanation(),
                entry.confidence(),
                new RiskHttpResponse(entry.riskScore(), entry.riskLevel(), entry.riskFactors()),
                entry.componentKey(),
                entry.owners(),
                entry.nextAction(),
                entry.uncoveredRanges().stream().map(CoverageLineRangeHttpResponse::from).toList(),
                entry.rangesTruncated());
    }

    public record RiskHttpResponse(BigDecimal score, String level, List<String> factors) {
    }
}
