package dev.vericov.integrations.application.port;

import dev.vericov.integrations.application.ProviderDefinition;
import java.util.List;
import java.util.Optional;

public interface ProviderRegistry {
    List<ProviderDefinition> listProviders(String type);

    Optional<ProviderDefinition> findProvider(String providerKey);

    ProviderDefinition requireProvider(String providerKey);
}
