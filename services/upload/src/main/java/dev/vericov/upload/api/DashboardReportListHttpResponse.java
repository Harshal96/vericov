package dev.vericov.upload.api;

import java.util.List;

public record DashboardReportListHttpResponse(List<DashboardReportListItemHttpResponse> reports) {
}
