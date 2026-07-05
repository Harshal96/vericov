package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardFileLineHit;
import jakarta.json.bind.annotation.JsonbProperty;

public record DashboardFileLineHitHttpResponse(
        @JsonbProperty("line_number") int lineNumber,
        long hits) {

    static DashboardFileLineHitHttpResponse from(DashboardFileLineHit lineHit) {
        return new DashboardFileLineHitHttpResponse(lineHit.lineNumber(), lineHit.hits());
    }
}
