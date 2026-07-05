package dev.vericov.upload.api;

import java.util.List;

public record DashboardRepositoryListHttpResponse(
        List<DashboardRepositoryOverviewHttpResponse> repos) {
}
