package dev.vericov.git.application;

import java.util.Map;
import java.util.Objects;

public record GitProviderActionResult(
        GitProviderActionType type,
        String providerId,
        String status,
        String providerUrl,
        Map<String, Object> metadata) {

    public GitProviderActionResult {
        Objects.requireNonNull(type, "type");
        providerId = GitValues.trimOptional(providerId);
        status = GitValues.requireCanonical(status, "status is required");
        providerUrl = GitValues.trimOptional(providerUrl);
        metadata = GitValues.deepCopyMap(metadata);
    }
}
