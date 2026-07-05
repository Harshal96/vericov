package dev.vericov.upload.api;

import java.util.List;

public record DashboardUploadListHttpResponse(List<DashboardUploadListItemHttpResponse> uploads) {
}
