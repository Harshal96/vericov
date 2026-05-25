package dev.vericov.upload.domain;

import java.util.Set;
import java.util.UUID;

public record RepositoryApiKeyPrincipal(
        UUID tenantId,
        UUID repositoryId,
        UUID apiKeyId,
        Set<String> scopes,
        Set<String> allowedBranches) {

    public RepositoryApiKeyPrincipal {
        scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
        allowedBranches = Set.copyOf(allowedBranches == null ? Set.of() : allowedBranches);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    public boolean allowsBranch(String branch) {
        if (allowedBranches.isEmpty() || allowedBranches.contains("*")) {
            return true;
        }
        return allowedBranches.stream().anyMatch(pattern -> branchMatches(pattern, branch));
    }

    private static boolean branchMatches(String pattern, String branch) {
        if (pattern.equals(branch)) {
            return true;
        }
        if (pattern.endsWith("*")) {
            return branch.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return false;
    }
}
