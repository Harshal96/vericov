package dev.vericov.analysis.application.port;

import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportSummary;
import dev.vericov.analysis.gates.GateEvaluation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoverageReportRepository {
    void save(CoverageReport report);

    default void save(CoverageReport report, List<GateEvaluation> evaluations) {
        save(report);
    }

    Optional<CoverageReportSummary> findLatestByCommit(UUID repositoryId, String commitSha);

    List<CoverageLineHit> findLineHits(UUID coverageReportId);

    List<CoverageLineHit> findLineHits(UUID coverageReportId, String filePath);
}
