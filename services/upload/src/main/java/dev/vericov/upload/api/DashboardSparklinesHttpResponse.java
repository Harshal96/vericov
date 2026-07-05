package dev.vericov.upload.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSparklinesHttpResponse(
        Map<String, List<BigDecimal>> sparklines) {
}
