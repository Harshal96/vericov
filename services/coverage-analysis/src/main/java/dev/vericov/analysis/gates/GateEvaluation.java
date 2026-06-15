package dev.vericov.analysis.gates;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

public record GateEvaluation(
        UUID id,
        UUID tenantId,
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
        String source,
        String scopeType,
        String scopeKey,
        List<String> scopePath,
        Instant evaluatedAt) {

    public GateEvaluation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(coverageReportId, "coverageReportId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(gateName, "gateName");
        Objects.requireNonNull(gateType, "gateType");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(status, "status");
        details = copyMap(details);
        source = source == null ? "repository" : source;
        scopeType = scopeType == null ? "repository" : scopeType;
        scopePath = List.copyOf(scopePath == null ? List.of() : scopePath);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public GateEvaluation(
            UUID id,
            UUID tenantId,
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
        this(
                id,
                tenantId,
                repositoryId,
                coverageReportId,
                commitSha,
                branch,
                pullRequestNumber,
                gateName,
                gateType,
                metric,
                threshold,
                actual,
                status,
                blocking,
                details,
                "repository",
                "repository",
                null,
                List.of(),
                evaluatedAt);
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
