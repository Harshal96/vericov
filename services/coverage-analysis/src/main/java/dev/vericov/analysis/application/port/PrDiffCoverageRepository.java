package dev.vericov.analysis.application.port;

import dev.vericov.analysis.diff.DiffCoverageReport;
import java.util.UUID;

public interface PrDiffCoverageRepository {
    void save(
            UUID tenantId,
            UUID repositoryId,
            UUID coverageReportId,
            int pullRequestNumber,
            String providerKey,
            String status,
            DiffCoverageReport report);
}
