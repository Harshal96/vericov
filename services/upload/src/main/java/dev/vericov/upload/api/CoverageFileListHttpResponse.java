package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageQueryService.FilePage;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record CoverageFileListHttpResponse(
        List<CoverageFileSummaryHttpResponse> files,
        boolean truncated,
        @JsonbProperty("next_cursor") String nextCursor,
        ResolvedCoverageRefHttpResponse resolved) {

    public static CoverageFileListHttpResponse from(FilePage page) {
        return new CoverageFileListHttpResponse(
                page.files().stream().map(CoverageFileSummaryHttpResponse::from).toList(),
                page.truncated(),
                page.nextCursor(),
                ResolvedCoverageRefHttpResponse.from(page.resolved()));
    }
}
