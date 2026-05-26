package dev.vericov.agent.application;

import java.util.List;
import java.util.UUID;

public record AgentTaskTarget(
        String filePath,
        int lineStart,
        int lineEnd,
        String riskLevel,
        UUID componentId,
        List<String> owners) {
    public AgentTaskTarget {
        filePath = AgentValues.requireTrimmed(filePath, "target.file_path is required");
        lineStart = AgentValues.requirePositive(lineStart, "target.line_start");
        lineEnd = AgentValues.requirePositive(lineEnd, "target.line_end");
        if (lineEnd < lineStart) {
            throw new AgentRunnerException("validation_error", "target.line_end must be greater than or equal to line_start");
        }
        riskLevel = AgentValues.requireRiskLevel(riskLevel);
        owners = AgentValues.copyStrings(owners);
    }
}
