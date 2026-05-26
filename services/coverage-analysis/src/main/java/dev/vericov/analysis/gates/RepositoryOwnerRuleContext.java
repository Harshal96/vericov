package dev.vericov.analysis.gates;

import java.util.List;

public record RepositoryOwnerRuleContext(
        String source,
        String pattern,
        List<String> owners,
        int priority) {

    public RepositoryOwnerRuleContext {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
