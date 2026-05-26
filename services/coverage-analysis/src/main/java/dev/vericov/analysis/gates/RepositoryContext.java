package dev.vericov.analysis.gates;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RepositoryContext(
        String contextVersion,
        List<Finding> findings,
        List<DebtItem> debtItems,
        Map<String, ComponentRollup> componentRollups,
        List<RepositoryComponentContext> components,
        List<RepositoryOwnerRuleContext> ownerRules,
        List<RepositoryPackageNodeContext> packageNodes) {

    public RepositoryContext {
        Objects.requireNonNull(contextVersion, "contextVersion");
        findings = findings == null ? List.of() : List.copyOf(findings);
        debtItems = debtItems == null ? List.of() : List.copyOf(debtItems);
        componentRollups = componentRollups == null ? Map.of() : Map.copyOf(componentRollups);
        components = components == null ? List.of() : List.copyOf(components);
        ownerRules = ownerRules == null ? List.of() : List.copyOf(ownerRules);
        packageNodes = packageNodes == null ? List.of() : List.copyOf(packageNodes);
    }

    public RepositoryContext(
            String contextVersion,
            List<Finding> findings,
            List<DebtItem> debtItems,
            Map<String, ComponentRollup> componentRollups) {
        this(contextVersion, findings, debtItems, componentRollups, List.of(), List.of(), List.of());
    }

    public RepositoryContext withComponentRollups(Map<String, ComponentRollup> nextComponentRollups) {
        return new RepositoryContext(
                contextVersion,
                findings,
                debtItems,
                nextComponentRollups,
                components,
                ownerRules,
                packageNodes);
    }
}
