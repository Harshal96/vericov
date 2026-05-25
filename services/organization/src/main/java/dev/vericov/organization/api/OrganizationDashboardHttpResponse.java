package dev.vericov.organization.api;

import dev.vericov.organization.application.OrganizationDashboardDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrganizationDashboardHttpResponse(
        @JsonbProperty("org_id")
        UUID organizationId,
        String branch,
        @JsonbProperty("repository_count")
        int repositoryCount,
        @JsonbProperty("average_line_coverage")
        BigDecimal averageLineCoverage,
        @JsonbProperty("failing_gate_count")
        int failingGateCount,
        List<RepositoryDashboardSummaryHttpResponse> repositories) {

    public static OrganizationDashboardHttpResponse from(OrganizationDashboardDetails details) {
        return new OrganizationDashboardHttpResponse(
                details.organizationId(),
                details.branch(),
                details.repositoryCount(),
                details.averageLineCoverage(),
                details.failingGateCount(),
                details.repositories().stream().map(RepositoryDashboardSummaryHttpResponse::from).toList());
    }
}
