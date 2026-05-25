package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateRepositoryApiKeyCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        List<String> scopes,
        List<String> branchAllowPatterns,
        Instant expiresAt) {

    public CreateRepositoryApiKeyCommand {
        scopes = List.copyOf(scopes == null ? List.of() : scopes);
        branchAllowPatterns = List.copyOf(branchAllowPatterns == null ? List.of() : branchAllowPatterns);
    }
}
