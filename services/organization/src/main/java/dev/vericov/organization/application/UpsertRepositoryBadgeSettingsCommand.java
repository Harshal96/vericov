package dev.vericov.organization.application;

import java.util.Map;
import java.util.UUID;

public record UpsertRepositoryBadgeSettingsCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        Boolean enabled,
        String branch,
        String metric,
        String label,
        Map<String, Object> thresholds) {
}
