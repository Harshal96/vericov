package dev.vericov.integrations.api;

import dev.vericov.integrations.application.ProviderDefinition;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.Map;

public record ProviderDefinitionHttpResponse(
        @JsonbProperty("provider_key")
        String providerKey,
        String type,
        @JsonbProperty("display_name")
        String displayName,
        @JsonbProperty("auth_strategy")
        String authStrategy,
        List<String> capabilities,
        @JsonbProperty("default_config")
        Map<String, Object> defaultConfig,
        @JsonbProperty("credential_kind_by_capability")
        Map<String, String> credentialKindByCapability) {

    public static ProviderDefinitionHttpResponse from(ProviderDefinition provider) {
        return new ProviderDefinitionHttpResponse(
                provider.providerKey(),
                provider.type(),
                provider.displayName(),
                provider.authStrategy(),
                provider.capabilities(),
                provider.defaultConfig(),
                provider.credentialKindByCapability());
    }
}
