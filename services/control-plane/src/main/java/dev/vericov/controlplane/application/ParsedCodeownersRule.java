package dev.vericov.controlplane.application;

import java.util.List;

public record ParsedCodeownersRule(
        String pattern,
        List<String> owners,
        int providerOrder) {

    public ParsedCodeownersRule {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
