package dev.vericov.organization.application;

import java.util.UUID;

public record GetOrganizationDashboardQuery(
        UUID requesterUserId,
        UUID organizationId,
        String branch) {
}
