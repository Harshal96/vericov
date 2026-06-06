package dev.vericov.upload.application;

public class DuplicateUploadException extends RuntimeException {
    public DuplicateUploadException(Throwable cause) {
        super(cause);
    }
}
