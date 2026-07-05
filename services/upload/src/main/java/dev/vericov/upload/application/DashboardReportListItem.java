package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.util.List;

public record DashboardReportListItem(
        DashboardReport report,
        BigDecimal lineDelta,
        String ciProvider,
        String ciBuildUrl,
        List<String> flags) {
    public DashboardReportListItem {
        flags = List.copyOf(flags == null ? List.of() : flags);
    }
}
