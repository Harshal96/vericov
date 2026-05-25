package dev.vericov.git.application.port;

public interface GitProviderClientFactory {
    GitProviderClient clientFor(String providerKey);
}
