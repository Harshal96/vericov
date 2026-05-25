package dev.vericov.organization.application;

import java.util.List;
import java.util.UUID;

public record UpsertRepositoryGatesCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        List<RepositoryGateDetails> gates) {

    public UpsertRepositoryGatesCommand {
        gates = List.copyOf(gates == null ? List.of() : gates);
    }
}
