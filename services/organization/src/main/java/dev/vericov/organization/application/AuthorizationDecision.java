package dev.vericov.organization.application;

public record AuthorizationDecision(
        boolean allowed,
        String code,
        String message) {

    public static AuthorizationDecision allow() {
        return new AuthorizationDecision(true, "allowed", "Allowed");
    }

    public static AuthorizationDecision deny(String code, String message) {
        return new AuthorizationDecision(false, code, message);
    }
}
