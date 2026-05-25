package dev.vericov.analysis.gates;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RepositoryContext(
        String contextVersion,
        List<Finding> findings,
        List<DebtItem> debtItems,
        Map<String, ComponentRollup> componentRollups) {

    public RepositoryContext {
        Objects.requireNonNull(contextVersion, "contextVersion");
        findings = findings == null ? List.of() : List.copyOf(findings);
        debtItems = debtItems == null ? List.of() : List.copyOf(debtItems);
        componentRollups = componentRollups == null ? Map.of() : Map.copyOf(componentRollups);
    }
}
