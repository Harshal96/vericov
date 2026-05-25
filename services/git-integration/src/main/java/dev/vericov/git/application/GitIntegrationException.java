package dev.vericov.git.application;

import java.util.Objects;

public class GitIntegrationException extends RuntimeException {
    private final String code;

    public GitIntegrationException(String code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = GitValues.requireCanonical(code, "code is required");
    }

    public String code() {
        return code;
    }
}
