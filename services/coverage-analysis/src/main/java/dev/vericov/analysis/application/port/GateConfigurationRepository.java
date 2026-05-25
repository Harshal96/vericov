package dev.vericov.analysis.application.port;

import dev.vericov.analysis.gates.GateConfiguration;
import java.util.List;
import java.util.UUID;

public interface GateConfigurationRepository {
    List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId);
}
