package dev.vericov.upload.application;

import java.time.Instant;
import java.util.UUID;

public record DashboardRepository(
        UUID id,
        String fullName,
        String provider,
        String defaultBranch,
        String visibility,
        String status,
        Instant updatedAt) {
}
