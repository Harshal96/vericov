package dev.vericov.integrations.api;

import dev.vericov.integrations.application.ResolvedIntegration;
import jakarta.json.bind.annotation.JsonbProperty;

public record ResolvedIntegrationHttpResponse(
        IntegrationConnectionHttpResponse connection,
        IntegrationBindingHttpResponse binding,
        @JsonbProperty("credential_kind")
        String credentialKind) {

    public static ResolvedIntegrationHttpResponse from(ResolvedIntegration resolved) {
        return new ResolvedIntegrationHttpResponse(
                IntegrationConnectionHttpResponse.from(resolved.connection()),
                IntegrationBindingHttpResponse.from(resolved.binding()),
                resolved.credentialKind());
    }
}
