package dev.vericov.upload.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record DashboardFileSummaryListHttpResponse(
        List<DashboardFileSummaryHttpResponse> files,
        @JsonbProperty("next_cursor") String nextCursor) {
}
