package dev.vericov.upload.application.port;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.DashboardRepository;
import dev.vericov.upload.application.DashboardRepositoryOverview;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DashboardQueryRepository {
    DashboardOverview overview(UUID tenantId);

    List<DashboardRepositoryOverview> repositories(UUID tenantId);

    Map<UUID, List<BigDecimal>> sparklines(UUID tenantId, int perRepository);

    Optional<DashboardRepository> repository(UUID tenantId, UUID repositoryId);
}
