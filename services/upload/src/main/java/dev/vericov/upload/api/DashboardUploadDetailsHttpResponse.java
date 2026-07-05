package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardUploadDetails;
import java.util.List;

public record DashboardUploadDetailsHttpResponse(
        DashboardUploadListItemHttpResponse upload,
        List<DashboardUploadEventHttpResponse> events) {
    public static DashboardUploadDetailsHttpResponse from(DashboardUploadDetails details) {
        return new DashboardUploadDetailsHttpResponse(
                DashboardUploadListItemHttpResponse.from(details.upload()),
                details.events().stream().map(DashboardUploadEventHttpResponse::from).toList());
    }
}
