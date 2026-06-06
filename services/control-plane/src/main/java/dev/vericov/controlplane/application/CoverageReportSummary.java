package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CoverageReportSummary(
        UUID id,
        UUID tenantId,
        UUID repositoryId,
        UUID uploadId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        int lineCovered,
        int lineTotal,
        int branchCovered,
        int branchTotal,
        int functionCovered,
        int functionTotal,
        int statementCovered,
        int statementTotal,
        Instant createdAt,
        Instant updatedAt) {

    public CoverageReportSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public CoverageReportSummary(
            UUID id,
            UUID tenantId,
            UUID repositoryId,
            UUID uploadId,
            String commitSha,
            String branch,
            int lineCovered,
            int lineTotal,
            int branchCovered,
            int branchTotal,
            int functionCovered,
            int functionTotal,
            int statementCovered,
            int statementTotal,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                tenantId,
                repositoryId,
                uploadId,
                commitSha,
                branch,
                null,
                lineCovered,
                lineTotal,
                branchCovered,
                branchTotal,
                functionCovered,
                functionTotal,
                statementCovered,
                statementTotal,
                createdAt,
                updatedAt);
    }

    int coveredForMetric(String metric) {
        return switch (metric) {
            case "line" -> lineCovered;
            case "branch" -> branchCovered;
            case "function" -> functionCovered;
            case "statement" -> statementCovered;
            default -> throw new OrganizationException("validation_error", "metric is invalid");
        };
    }

    int totalForMetric(String metric) {
        return switch (metric) {
            case "line" -> lineTotal;
            case "branch" -> branchTotal;
            case "function" -> functionTotal;
            case "statement" -> statementTotal;
            default -> throw new OrganizationException("validation_error", "metric is invalid");
        };
    }
}
