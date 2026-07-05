package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageQueryService.ComponentCoverageResult;
import java.util.List;

public record ComponentCoverageListHttpResponse(
        List<ComponentCoverageHttpResponse> components,
        ResolvedCoverageRefHttpResponse resolved) {

    public static ComponentCoverageListHttpResponse from(ComponentCoverageResult result) {
        return new ComponentCoverageListHttpResponse(
                result.components().stream().map(ComponentCoverageHttpResponse::from).toList(),
                ResolvedCoverageRefHttpResponse.from(result.resolved()));
    }
}
