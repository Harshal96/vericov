package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardFileSummary;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record DashboardFileSummaryHttpResponse(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("package_name") String packageName,
        List<String> owners,
        @JsonbProperty("line_covered") long lineCovered,
        @JsonbProperty("line_total") long lineTotal,
        @JsonbProperty("branch_covered") long branchCovered,
        @JsonbProperty("branch_total") long branchTotal,
        @JsonbProperty("function_covered") long functionCovered,
        @JsonbProperty("function_total") long functionTotal,
        @JsonbProperty("statement_covered") long statementCovered,
        @JsonbProperty("statement_total") long statementTotal) {

    static DashboardFileSummaryHttpResponse from(DashboardFileSummary file) {
        return new DashboardFileSummaryHttpResponse(
                file.filePath(),
                file.packageName(),
                file.owners(),
                file.line().covered(),
                file.line().total(),
                file.branchCoverage().covered(),
                file.branchCoverage().total(),
                file.function().covered(),
                file.function().total(),
                file.statement().covered(),
                file.statement().total());
    }
}
