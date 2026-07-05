package dev.vericov.upload.api;

import java.util.List;

public record DashboardGapListHttpResponse(List<DashboardGapFindingHttpResponse> gaps) {
}
