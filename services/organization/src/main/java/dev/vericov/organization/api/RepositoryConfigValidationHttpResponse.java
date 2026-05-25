package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryConfigDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.Map;

public record RepositoryConfigValidationHttpResponse(
        @JsonbProperty("validation_status")
        String validationStatus,
        @JsonbProperty("validation_errors")
        List<String> validationErrors,
        Map<String, Object> config,
        @JsonbProperty("schema_version")
        int schemaVersion) {

    public static RepositoryConfigValidationHttpResponse from(RepositoryConfigDetails details) {
        return new RepositoryConfigValidationHttpResponse(
                details.validationStatus(),
                details.validationErrors(),
                details.config(),
                details.schemaVersion());
    }
}
