package dev.vericov.git.adapter.provider;

import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;
import dev.vericov.git.application.port.GitProviderClient;

public class UnsupportedGitProviderClient implements GitProviderClient {
    private final String providerKey;

    public UnsupportedGitProviderClient(String providerKey) {
        this.providerKey = providerKey == null ? "unknown" : providerKey.trim();
    }

    @Override
    public GitProviderActionResult execute(GitProviderAction action) {
        throw new GitIntegrationException(
                "unsupported_provider",
                "Git provider is not supported for execution: " + providerKey);
    }
}
