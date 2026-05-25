package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.application.port.CoverageAnalysisProcessor;
import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import dev.vericov.analysis.coverage.CoverageParserRegistry;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportMerger;
import dev.vericov.analysis.coverage.ParsedCoverage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import dev.vericov.analysis.gates.GateEvaluation;
import dev.vericov.analysis.gates.GateEvaluator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultCoverageAnalysisProcessor implements CoverageAnalysisProcessor {
    private final CoverageAnalysisInputRepository inputs;
    private final ArtifactContentStore contentStore;
    private final CoverageReportRepository reports;
    private final GateConfigurationRepository gates;
    private final CoverageParserRegistry parserRegistry;
    private final PrDiffCoverageProcessor prDiffCoverageProcessor;
    private final CoverageReportMerger merger = new CoverageReportMerger();
    private final GateEvaluator gateEvaluator = new GateEvaluator();
    private final Clock clock;

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            CoverageParserRegistry parserRegistry,
            Clock clock) {
        this(inputs, contentStore, reports, emptyGateConfigurationRepository(), parserRegistry, PrDiffCoverageProcessor.noop(), clock);
    }

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            GateConfigurationRepository gates,
            CoverageParserRegistry parserRegistry,
            Clock clock) {
        this(inputs, contentStore, reports, gates, parserRegistry, PrDiffCoverageProcessor.noop(), clock);
    }

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            CoverageParserRegistry parserRegistry,
            PrDiffCoverageProcessor prDiffCoverageProcessor,
            Clock clock) {
        this(inputs, contentStore, reports, emptyGateConfigurationRepository(), parserRegistry, prDiffCoverageProcessor, clock);
    }

    public DefaultCoverageAnalysisProcessor(
            CoverageAnalysisInputRepository inputs,
            ArtifactContentStore contentStore,
            CoverageReportRepository reports,
            GateConfigurationRepository gates,
            CoverageParserRegistry parserRegistry,
            PrDiffCoverageProcessor prDiffCoverageProcessor,
            Clock clock) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.gates = Objects.requireNonNull(gates, "gates");
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
        List<GateEvaluation> evaluations = gateEvaluator.evaluate(
                report,
                gates.listActiveForRepository(report.tenantId(), report.repositoryId()),
                report.generatedAt());
        reports.save(report, evaluations);
        prDiffCoverageProcessor.process(input, report);
    }

    private static GateConfigurationRepository emptyGateConfigurationRepository() {
        return (tenantId, repositoryId) -> List.of();
    }
}
