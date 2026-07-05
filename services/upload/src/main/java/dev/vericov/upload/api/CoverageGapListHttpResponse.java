package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageQueryService.GapPage;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record CoverageGapListHttpResponse(
        List<CoverageGapFindingHttpResponse> gaps,
        boolean truncated,
        @JsonbProperty("next_cursor") String nextCursor,
        ResolvedCoverageRefHttpResponse resolved) {

    public static CoverageGapListHttpResponse from(GapPage page) {
        return new CoverageGapListHttpResponse(
                page.gaps().stream().map(CoverageGapFindingHttpResponse::from).toList(),
                page.truncated(),
                page.nextCursor(),
                ResolvedCoverageRefHttpResponse.from(page.resolved()));
    }
}
