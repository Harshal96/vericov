package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.AuthorizationDecision;

public record AuthorizationDecisionHttpResponse(
        boolean allowed,
        String code,
        String message) {

    public static AuthorizationDecisionHttpResponse from(AuthorizationDecision decision) {
        return new AuthorizationDecisionHttpResponse(decision.allowed(), decision.code(), decision.message());
    }
}
