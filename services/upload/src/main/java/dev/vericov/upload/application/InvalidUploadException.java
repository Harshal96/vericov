package dev.vericov.upload.application;

public class InvalidUploadException extends RuntimeException {
    private final String code;

    public InvalidUploadException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
