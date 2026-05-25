package dev.vericov.analysis.gates;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GateEvaluation(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        UUID coverageReportId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String gateName,
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking,
        Map<String, Object> details,
        Instant evaluatedAt) {

    public GateEvaluation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(coverageReportId, "coverageReportId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(gateName, "gateName");
        Objects.requireNonNull(gateType, "gateType");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(status, "status");
        details = copyMap(details);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
