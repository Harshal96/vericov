package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.application.port.CoverageAnalysisProcessor;
import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.application.port.NormalizedCoverageLocation;
import dev.vericov.analysis.application.port.NormalizedCoverageStore;
import dev.vericov.analysis.application.port.RepositoryContextRepository;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import dev.vericov.analysis.coverage.CoverageParserRegistry;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportMerger;
import dev.vericov.analysis.coverage.ParsedCoverage;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import dev.vericov.analysis.gates.GateEvaluation;
import dev.vericov.analysis.gates.GateEvaluator;
import dev.vericov.analysis.gates.RepositoryContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultCoverageAnalysisProcessor implements CoverageAnalysisProcessor {
    private final CoverageAnalysisInputRepository inputs;
    private final ArtifactContentStore contentStore;
    private final CoverageReportRepository reports;
    private final GateConfigurationRepository gates;
    private final RepositoryContextRepository contextRepository;
    private final NormalizedCoverageStore normalizedCoverageStore;
    private final CoverageParserRegistry parserRegistry;
    private final PrDiffCoverageProcessor prDiffCoverageProcessor;
    private final CoverageReportMerger merger = new CoverageReportMerger();
    private final GateEvaluator gateEvaluator = new GateEvaluator();
    private final Clock clock;

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            NormalizedCoverageStore normalizedCoverageStore,
            CoverageParserRegistry parserRegistry,
            Clock clock) {
        this(
                inputs,
                contentStore,
                reports,
                emptyGateConfigurationRepository(),
                emptyRepositoryContextRepository(),
                normalizedCoverageStore,
                parserRegistry,
                PrDiffCoverageProcessor.noop(),
                clock);
    }

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            GateConfigurationRepository gates,
            NormalizedCoverageStore normalizedCoverageStore,
            CoverageParserRegistry parserRegistry,
            Clock clock) {
        this(
                inputs,
                contentStore,
                reports,
                gates,
                emptyRepositoryContextRepository(),
                normalizedCoverageStore,
                parserRegistry,
                PrDiffCoverageProcessor.noop(),
                clock);
    }

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            NormalizedCoverageStore normalizedCoverageStore,
            CoverageParserRegistry parserRegistry,
            PrDiffCoverageProcessor prDiffCoverageProcessor,
            Clock clock) {
        this(
                inputs,
                contentStore,
                reports,
                emptyGateConfigurationRepository(),
                emptyRepositoryContextRepository(),
                normalizedCoverageStore,
                parserRegistry,
                prDiffCoverageProcessor,
                clock);
    }

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            GateConfigurationRepository gates,
            NormalizedCoverageStore normalizedCoverageStore,
            CoverageParserRegistry parserRegistry,
            PrDiffCoverageProcessor prDiffCoverageProcessor,
            Clock clock) {
        this(
                inputs,
                contentStore,
                reports,
                gates,
                emptyRepositoryContextRepository(),
                normalizedCoverageStore,
                parserRegistry,
                prDiffCoverageProcessor,
                clock);
    }

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            GateConfigurationRepository gates,
            RepositoryContextRepository contextRepository,
            NormalizedCoverageStore normalizedCoverageStore,
            CoverageParserRegistry parserRegistry,
            PrDiffCoverageProcessor prDiffCoverageProcessor,
            Clock clock) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.gates = Objects.requireNonNull(gates, "gates");
        this.contextRepository = Objects.requireNonNull(contextRepository, "contextRepository");
        this.normalizedCoverageStore = Objects.requireNonNull(normalizedCoverageStore, "normalizedCoverageStore");
        this.parserRegistry = Objects.requireNonNull(parserRegistry, "parserRegistry");
        this.prDiffCoverageProcessor = Objects.requireNonNull(prDiffCoverageProcessor, "prDiffCoverageProcessor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void process(UploadReceivedEvent event) {
        CoverageAnalysisInput input = inputs.load(event.uploadId());
        List<CoverageInputArtifact> coverageArtifacts = input.coverageArtifacts();
        if (coverageArtifacts.isEmpty()) {
            throw new IllegalStateException("No coverage artifacts found for upload " + event.uploadId());
        }

        List<ParsedCoverage> parsedCoverages = new ArrayList<>();
        for (CoverageInputArtifact artifact : coverageArtifacts) {
            byte[] content = contentStore.read(artifact.storageBucket(), artifact.storagePath());
            parsedCoverages.add(parserRegistry.parse(artifact, content));
        }

        CoverageReport report = merger.merge(input, parsedCoverages, clock.instant());
        NormalizedCoverageLocation location = normalizedCoverageStore.store(report);
        CoverageReport reportWithStorage = report.withNormalizedStorage(location.bucket(), location.path());

        // 1. Process diff coverage first so we have the DiffCoverageReport
        DiffCoverageReport diffCoverage = prDiffCoverageProcessor.process(input, reportWithStorage);

        // 2. Load RepositoryContext
        RepositoryContext repositoryContext = contextRepository.loadContext(
                reportWithStorage.tenantId(),
                reportWithStorage.repositoryId(),
                reportWithStorage.commitSha(),
                reportWithStorage.branchName(),
                reportWithStorage.pullRequestNumber());

        // 3. Evaluate gates with report, context, and diff coverage
        List<GateEvaluation> evaluations = gateEvaluator.evaluate(
                reportWithStorage,
                gates.listActiveForRepository(reportWithStorage.tenantId(), reportWithStorage.repositoryId()),
                repositoryContext,
                diffCoverage,
                reportWithStorage.generatedAt());

        // 4. Save report and evaluations
        reports.save(reportWithStorage, evaluations);
    }

    private static GateConfigurationRepository emptyGateConfigurationRepository() {
        return (tenantId, repositoryId) -> List.of();
    }

    private static RepositoryContextRepository emptyRepositoryContextRepository() {
        return (tenantId, repositoryId, commitSha, branch, pr) -> new RepositoryContext(
                "ctx-" + Instant.now().toString(),
                List.of(),
                List.of(),
                Map.of());
    }
}
