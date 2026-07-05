package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageGapFindingDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.util.List;

public record CoverageGapFindingHttpResponse(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("target_type") String targetType,
        @JsonbProperty("line_start") Integer lineStart,
        @JsonbProperty("line_end") Integer lineEnd,
        @JsonbProperty("symbol_name") String symbolName,
        @JsonbProperty("reason_code") String reasonCode,
        String explanation,
        String confidence,
        @JsonbProperty("risk_score") BigDecimal riskScore,
        @JsonbProperty("risk_level") String riskLevel,
        List<String> owners,
        @JsonbProperty("component_key") String componentKey,
        @JsonbProperty("next_action") String nextAction,
        String status) {

    public static CoverageGapFindingHttpResponse from(CoverageGapFindingDetails finding) {
        return new CoverageGapFindingHttpResponse(
                finding.filePath(),
                finding.targetType(),
                finding.lineStart(),
                finding.lineEnd(),
                finding.symbolName(),
                finding.reasonCode(),
                finding.explanation(),
                finding.confidence(),
                finding.riskScore(),
                finding.riskLevel(),
                finding.owners(),
                finding.componentKey(),
                finding.nextAction(),
                finding.status());
    }
}
