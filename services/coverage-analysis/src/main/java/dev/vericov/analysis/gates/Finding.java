package dev.vericov.analysis.gates;

import java.util.Objects;
import java.util.UUID;

public record Finding(
        UUID id,
        String filePath,
        Integer line,
        String riskLevel,
        String status) {

    public Finding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(status, "status");
    }
}
