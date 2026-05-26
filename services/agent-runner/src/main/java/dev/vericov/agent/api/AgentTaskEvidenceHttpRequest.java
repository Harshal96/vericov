package dev.vericov.agent.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;

public record AgentTaskEvidenceHttpRequest(
        @JsonbProperty("reason_code") String reasonCode,
        @JsonbProperty("risk_score") double riskScore,
        @JsonbProperty("context_version") String contextVersion,
        Map<String, Object> metadata) {
}
