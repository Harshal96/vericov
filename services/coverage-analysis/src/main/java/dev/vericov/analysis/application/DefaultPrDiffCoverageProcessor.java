package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.PrDiffCoverageRepository;
import dev.vericov.analysis.application.port.PullRequestDiffClient;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportSummary;
import dev.vericov.analysis.diff.DiffCoverageCalculator;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.diff.PullRequestDiff;
import java.util.List;
import java.util.Objects;

public class DefaultPrDiffCoverageProcessor implements PrDiffCoverageProcessor {
    private final PullRequestDiffClient diffClient;
    private final CoverageReportRepository reports;
    private final PrDiffCoverageRepository diffCoverageRepository;
    private final DiffCoverageCalculator calculator = new DiffCoverageCalculator();

    public DefaultPrDiffCoverageProcessor(
            PullRequestDiffClient diffClient,
            CoverageReportRepository reports,
            PrDiffCoverageRepository diffCoverageRepository) {
        this.diffClient = Objects.requireNonNull(diffClient, "diffClient");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.diffCoverageRepository = Objects.requireNonNull(diffCoverageRepository, "diffCoverageRepository");
    }

    @Override
    public DiffCoverageReport process(CoverageAnalysisInput input, CoverageReport headReport) {
        if (input.pullRequestNumber() == null) {
            return null;
        }
        PullRequestDiff diff = diffClient.fetch(input, headReport);
        if (!headReport.commitSha().equals(diff.headSha())) {
            throw new IllegalStateException("PR diff head SHA does not match coverage report commit");
        }
        BaseCoverage baseCoverage = reports.findLatestByCommit(input.repositoryId(), diff.baseSha())
                .map(summary -> new BaseCoverage("complete", reports.findLineHits(summary.reportId())))
                .orElseGet(() -> new BaseCoverage("base_coverage_missing", List.of()));
        DiffCoverageReport diffCoverage = calculator.calculate(diff, headReport.lineHits(), baseCoverage.lineHits())
                .withStatus(baseCoverage.status());
        diffCoverageRepository.save(
                headReport.tenantId(),
                headReport.repositoryId(),
                headReport.reportId(),
                input.pullRequestNumber(),
                input.provider(),
                baseCoverage.status(),
                diffCoverage);
        return diffCoverage;
    }

    private record BaseCoverage(String status, List<CoverageLineHit> lineHits) {
    }
}
