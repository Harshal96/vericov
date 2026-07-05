package dev.vericov.upload.application;

import java.util.List;

public record DashboardUploadDetails(DashboardUploadListItem upload, List<DashboardUploadEvent> events) {
    public DashboardUploadDetails {
        events = List.copyOf(events == null ? List.of() : events);
    }
}
