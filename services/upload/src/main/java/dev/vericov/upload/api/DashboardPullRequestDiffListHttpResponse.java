package dev.vericov.upload.api;

import java.util.List;

public record DashboardPullRequestDiffListHttpResponse(List<DashboardPullRequestDiffHttpResponse> diffs) {
}
