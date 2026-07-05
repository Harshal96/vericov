package dev.vericov.upload.api;

import java.util.List;

public record DashboardUploadArtifactListHttpResponse(List<DashboardUploadArtifactHttpResponse> artifacts) {
}
