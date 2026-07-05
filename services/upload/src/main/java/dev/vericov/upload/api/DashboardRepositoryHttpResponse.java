package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardRepository;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record DashboardRepositoryHttpResponse(
        UUID id,
        @JsonbProperty("full_name") String fullName,
        String provider,
        @JsonbProperty("default_branch") String defaultBranch,
        String visibility,
        String status,
        @JsonbProperty("updated_at") Instant updatedAt) {
    public static DashboardRepositoryHttpResponse from(DashboardRepository repository) {
        return new DashboardRepositoryHttpResponse(
                repository.id(),
                repository.fullName(),
                repository.provider(),
                repository.defaultBranch(),
                repository.visibility(),
                repository.status(),
                repository.updatedAt());
    }
}
