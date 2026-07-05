package dev.vericov.upload.application;

import java.util.List;

public record DashboardPullRequestDiffDetails(
        DashboardPullRequestDiff diff,
        List<DashboardPullRequestDiffFile> files) {
    public DashboardPullRequestDiffDetails {
        files = List.copyOf(files == null ? List.of() : files);
    }
}
