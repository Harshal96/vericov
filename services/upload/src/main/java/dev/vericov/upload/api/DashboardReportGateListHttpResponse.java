package dev.vericov.upload.api;

import java.util.List;

public record DashboardReportGateListHttpResponse(List<DashboardGateEvaluationHttpResponse> gates) {
}
