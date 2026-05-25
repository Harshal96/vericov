package dev.vericov.integrations.application;

public class IntegrationException extends RuntimeException {
    private final String code;

    public IntegrationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
