package dev.vericov.ignore;

public final class InvalidCoverageIgnoreRuleException extends IllegalArgumentException {
    private final String code;
    private final int index;

    public InvalidCoverageIgnoreRuleException(String code, int index, String message) {
        super(message);
        this.code = code;
        this.index = index;
    }

    public String code() {
        return code;
    }

    public int index() {
        return index;
    }
}
