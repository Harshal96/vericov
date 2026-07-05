package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageQueryService.GateResult;
import java.util.List;

public record CoverageGateListHttpResponse(
        List<CoverageGateEvaluationHttpResponse> gates,
        ResolvedCoverageRefHttpResponse resolved) {

    public static CoverageGateListHttpResponse from(GateResult result) {
        return new CoverageGateListHttpResponse(
                result.gates().stream().map(CoverageGateEvaluationHttpResponse::from).toList(),
                ResolvedCoverageRefHttpResponse.from(result.resolved()));
    }
}
