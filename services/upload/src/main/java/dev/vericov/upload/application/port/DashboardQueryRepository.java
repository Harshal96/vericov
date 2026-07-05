package dev.vericov.upload.application.port;

import dev.vericov.upload.application.DashboardOverview;
import java.util.UUID;

public interface DashboardQueryRepository {
    DashboardOverview overview(UUID tenantId);
}
