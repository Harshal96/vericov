package dev.vericov.controlplane.application;

import java.util.List;

public record RepositoryContextSyncDetails(
        List<RepositoryOwnerRuleDetails> ownerRules,
        List<RepositoryPackageNodeDetails> packageNodes) {

    public RepositoryContextSyncDetails {
        ownerRules = List.copyOf(ownerRules == null ? List.of() : ownerRules);
        packageNodes = List.copyOf(packageNodes == null ? List.of() : packageNodes);
    }
}
