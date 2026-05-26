package dev.vericov.agent.api;

import dev.vericov.agent.application.AgentRunnerException;
import java.util.List;
import java.util.UUID;

final class ApiParsing {
    private ApiParsing() {
    }

    static UUID parseRequiredUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AgentRunnerException("validation_error", fieldName + " is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new AgentRunnerException("validation_error", fieldName + " is invalid");
        }
    }

    static UUID parseOptionalUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredUuid(value, fieldName);
    }

    static List<UUID> parseUuidList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new AgentRunnerException("validation_error", fieldName + " is required");
        }
        return values.stream()
                .map(value -> parseRequiredUuid(value, fieldName))
                .toList();
    }
}
