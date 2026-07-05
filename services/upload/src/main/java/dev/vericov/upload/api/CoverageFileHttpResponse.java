package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageQueryService.FileDetailResult;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record CoverageFileHttpResponse(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("leaf_component_key") String leafComponentKey,
        List<String> owners,
        CoverageMetricHttpResponse line,
        @JsonbProperty("branch_coverage") CoverageMetricHttpResponse branchCoverage,
        CoverageMetricHttpResponse function,
        CoverageMetricHttpResponse statement,
        @JsonbProperty("uncovered_ranges") List<CoverageLineRangeHttpResponse> uncoveredRanges,
        ResolvedCoverageRefHttpResponse resolved) {

    public static CoverageFileHttpResponse from(FileDetailResult result) {
        var file = result.file();
        return new CoverageFileHttpResponse(
                file.filePath(),
                file.leafComponentKey(),
                file.owners(),
                CoverageMetricHttpResponse.from(file.line()),
                CoverageMetricHttpResponse.from(file.branchCoverage()),
                CoverageMetricHttpResponse.from(file.function()),
                CoverageMetricHttpResponse.from(file.statement()),
                file.uncoveredRanges().stream().map(CoverageLineRangeHttpResponse::from).toList(),
                ResolvedCoverageRefHttpResponse.from(result.resolved()));
    }
}
