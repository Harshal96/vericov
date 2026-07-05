package dev.vericov.upload.application.port;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.DashboardComponentRollup;
import dev.vericov.upload.application.DashboardFileLineHit;
import dev.vericov.upload.application.DashboardFileSummary;
import dev.vericov.upload.application.DashboardGapCounts;
import dev.vericov.upload.application.DashboardGapFinding;
import dev.vericov.upload.application.DashboardGateConfig;
import dev.vericov.upload.application.DashboardGateEvaluation;
import dev.vericov.upload.application.DashboardReport;
import dev.vericov.upload.application.DashboardReportDetails;
import dev.vericov.upload.application.DashboardReportListItem;
import dev.vericov.upload.application.DashboardRepository;
import dev.vericov.upload.application.DashboardRepositoryOverview;
import dev.vericov.upload.application.DashboardTrendPoint;
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

    List<DashboardTrendPoint> trend(UUID tenantId, UUID repositoryId, String branch, int limit);

    List<DashboardReportListItem> reports(UUID tenantId, UUID repositoryId, int limit);

    Optional<DashboardReportDetails> report(UUID tenantId, UUID reportId);

    default List<DashboardFileSummary> reportFiles(UUID tenantId, UUID reportId) {
        return List.of();
    }

    default boolean reportFileExists(UUID tenantId, UUID reportId, String filePath) {
        return false;
    }

    default List<DashboardFileLineHit> reportLineHits(UUID tenantId, UUID reportId, String filePath) {
        return List.of();
    }

    default List<String> similarReportFilePaths(UUID tenantId, UUID reportId, String basename, int max) {
        return List.of();
    }

    default List<DashboardComponentRollup> reportComponents(UUID tenantId, UUID reportId) {
        return List.of();
    }

    default List<DashboardGapFinding> gaps(UUID tenantId, String status, int limit) {
        return List.of();
    }

    default List<DashboardGapFinding> repositoryGaps(UUID tenantId, UUID repositoryId, String status, int limit) {
        return List.of();
    }

    default DashboardGapCounts repositoryGapCounts(UUID tenantId, UUID repositoryId) {
        return new DashboardGapCounts(0, 0, 0, 0);
    }

    default List<DashboardGateConfig> gateConfigs(UUID tenantId, UUID repositoryId) {
        return List.of();
    }

    default List<DashboardGateEvaluation> repositoryGateEvaluations(UUID tenantId, UUID repositoryId, int limit) {
        return List.of();
    }

    default List<DashboardGateEvaluation> reportGates(UUID tenantId, UUID reportId) {
        return List.of();
    }
}
