package dev.vericov.analysis.domain;

public final class NonRetryableAnalysisException extends IllegalStateException {
    public NonRetryableAnalysisException(String message) {
        super(message);
    }

    public NonRetryableAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
