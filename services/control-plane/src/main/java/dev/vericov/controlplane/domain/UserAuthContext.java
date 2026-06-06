package dev.vericov.controlplane.domain;

public record UserAuthContext(
        String authorizationHeader,
        String userIdHeader,
        String userEmailHeader) {

    public UserAuthContext(String authorizationHeader, String userIdHeader) {
        this(authorizationHeader, userIdHeader, null);
    }
}
