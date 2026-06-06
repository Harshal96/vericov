package dev.vericov.controlplane.application;

import java.util.List;
import java.util.UUID;

public record SyncRepositoryContextCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String sourceRef,
        String codeownersText,
        List<RepositoryPackageNodeInput> packageNodes) {

    public SyncRepositoryContextCommand {
        packageNodes = List.copyOf(packageNodes == null ? List.of() : packageNodes);
    }
}
