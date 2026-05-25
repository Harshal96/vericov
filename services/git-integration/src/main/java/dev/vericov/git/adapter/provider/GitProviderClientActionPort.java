package dev.vericov.git.adapter.provider;

import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;
import dev.vericov.git.application.port.GitProviderActionPort;
import dev.vericov.git.application.port.GitProviderClientFactory;
import java.util.Objects;

public class GitProviderClientActionPort implements GitProviderActionPort {
    private final GitProviderClientFactory clientFactory;

    public GitProviderClientActionPort(GitProviderClientFactory clientFactory) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
    }

    @Override
    public GitProviderActionResult execute(GitProviderAction action) {
        return clientFactory.clientFor(action.providerKey()).execute(action);
    }
}
