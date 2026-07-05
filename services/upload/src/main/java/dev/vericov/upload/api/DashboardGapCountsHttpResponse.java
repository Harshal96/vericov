package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardGapCounts;

public record DashboardGapCountsHttpResponse(long critical, long high, long medium, long low) {
    public static DashboardGapCountsHttpResponse from(DashboardGapCounts counts) {
        return new DashboardGapCountsHttpResponse(
                counts.critical(),
                counts.high(),
                counts.medium(),
                counts.low());
    }
}
