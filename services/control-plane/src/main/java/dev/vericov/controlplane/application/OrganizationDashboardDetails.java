package dev.vericov.controlplane.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrganizationDashboardDetails(
        UUID organizationId,
        String branch,
        int repositoryCount,
        BigDecimal averageLineCoverage,
        int failingGateCount,
        List<RepositoryDashboardSummaryDetails> repositories) {

    public OrganizationDashboardDetails {
        repositories = List.copyOf(repositories == null ? List.of() : repositories);
    }
}
