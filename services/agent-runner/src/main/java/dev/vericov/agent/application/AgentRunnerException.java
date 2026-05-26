package dev.vericov.agent.application;

import java.util.Objects;

public class AgentRunnerException extends RuntimeException {
    private final String code;

    public AgentRunnerException(String code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = AgentValues.requireCanonical(code, "code is required");
    }

    public String code() {
        return code;
    }
}
