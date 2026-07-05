package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageQueryService.CoverageSummary;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record CoverageSummaryHttpResponse(
        CoverageMetricHttpResponse line,
        @JsonbProperty("branch_coverage") CoverageMetricHttpResponse branchCoverage,
        CoverageMetricHttpResponse function,
        CoverageMetricHttpResponse statement,
        @JsonbProperty("gate_status") String gateStatus,
        @JsonbProperty("config_sha256") String configSha256,
        List<CoverageWarningHttpResponse> warnings,
        ResolvedCoverageRefHttpResponse resolved) {

    public static CoverageSummaryHttpResponse from(CoverageSummary summary) {
        var report = summary.report();
        return new CoverageSummaryHttpResponse(
                CoverageMetricHttpResponse.from(report.line()),
                CoverageMetricHttpResponse.from(report.branchCoverage()),
                CoverageMetricHttpResponse.from(report.function()),
                CoverageMetricHttpResponse.from(report.statement()),
                report.gateStatus(),
                report.configSha256(),
                report.warnings().stream().map(CoverageWarningHttpResponse::from).toList(),
                ResolvedCoverageRefHttpResponse.from(summary.resolved()));
    }
}
