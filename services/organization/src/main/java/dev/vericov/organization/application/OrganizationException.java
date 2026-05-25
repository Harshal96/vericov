package dev.vericov.organization.application;

public class OrganizationException extends RuntimeException {
    private final String code;

    public OrganizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
