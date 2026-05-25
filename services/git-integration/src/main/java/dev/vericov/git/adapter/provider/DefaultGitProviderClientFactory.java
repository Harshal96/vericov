package dev.vericov.git.adapter.provider;

import dev.vericov.git.application.port.GitProviderClient;
import dev.vericov.git.application.port.GitProviderClientFactory;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class DefaultGitProviderClientFactory implements GitProviderClientFactory {
    private final Map<String, GitProviderClient> clientsByProvider;

    public DefaultGitProviderClientFactory(Map<String, GitProviderClient> clientsByProvider) {
        this.clientsByProvider = Map.copyOf(Objects.requireNonNull(clientsByProvider, "clientsByProvider"));
    }

    @Override
    public GitProviderClient clientFor(String providerKey) {
        String normalized = providerKey == null ? "" : providerKey.trim().toLowerCase(Locale.ROOT);
        GitProviderClient client = clientsByProvider.get(normalized);
        if (client == null) {
            return new UnsupportedGitProviderClient(normalized);
        }
        return client;
    }
}
