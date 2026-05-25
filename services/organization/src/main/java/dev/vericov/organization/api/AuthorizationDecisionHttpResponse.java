package dev.vericov.organization.api;

import dev.vericov.organization.application.AuthorizationDecision;

public record AuthorizationDecisionHttpResponse(
        boolean allowed,
        String code,
        String message) {

    public static AuthorizationDecisionHttpResponse from(AuthorizationDecision decision) {
        return new AuthorizationDecisionHttpResponse(decision.allowed(), decision.code(), decision.message());
    }
}
