package dev.vericov.upload.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record DashboardTestRunListHttpResponse(
        @JsonbProperty("test_runs") List<DashboardTestRunHttpResponse> testRuns) {
}
