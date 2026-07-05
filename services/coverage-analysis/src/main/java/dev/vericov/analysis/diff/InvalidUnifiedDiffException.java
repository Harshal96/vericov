package dev.vericov.analysis.diff;

public final class InvalidUnifiedDiffException extends RuntimeException {
    public InvalidUnifiedDiffException(String message) {
        super(message);
    }

    public InvalidUnifiedDiffException(String message, Throwable cause) {
        super(message, cause);
    }
}
