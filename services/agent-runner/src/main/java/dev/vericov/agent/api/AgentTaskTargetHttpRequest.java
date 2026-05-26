package dev.vericov.agent.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record AgentTaskTargetHttpRequest(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("line_start") int lineStart,
        @JsonbProperty("line_end") int lineEnd,
        @JsonbProperty("risk_level") String riskLevel,
        @JsonbProperty("component_id") String componentId,
        List<String> owners) {
}
