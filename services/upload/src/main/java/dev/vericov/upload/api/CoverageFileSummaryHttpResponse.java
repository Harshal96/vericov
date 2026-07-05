package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageFileSummaryDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record CoverageFileSummaryHttpResponse(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("leaf_component_key") String leafComponentKey,
        List<String> owners,
        CoverageMetricHttpResponse line,
        @JsonbProperty("branch_coverage") CoverageMetricHttpResponse branchCoverage,
        CoverageMetricHttpResponse function,
        CoverageMetricHttpResponse statement) {

    public static CoverageFileSummaryHttpResponse from(CoverageFileSummaryDetails file) {
        return new CoverageFileSummaryHttpResponse(
                file.filePath(),
                file.leafComponentKey(),
                file.owners(),
                CoverageMetricHttpResponse.from(file.line()),
                CoverageMetricHttpResponse.from(file.branchCoverage()),
                CoverageMetricHttpResponse.from(file.function()),
                CoverageMetricHttpResponse.from(file.statement()));
    }
}
