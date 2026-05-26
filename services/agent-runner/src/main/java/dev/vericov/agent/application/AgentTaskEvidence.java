package dev.vericov.agent.application;

import java.util.Map;

public record AgentTaskEvidence(
        String reasonCode,
        double riskScore,
        String contextVersion,
        Map<String, Object> metadata) {
    public AgentTaskEvidence {
        reasonCode = AgentValues.requireCanonical(reasonCode, "evidence.reason_code is required");
        if (riskScore < 0 || riskScore > 100) {
            throw new AgentRunnerException("validation_error", "evidence.risk_score must be between 0 and 100");
        }
        contextVersion = AgentValues.requireTrimmed(contextVersion, "evidence.context_version is required");
        metadata = AgentValues.copyMap(metadata);
    }
}
