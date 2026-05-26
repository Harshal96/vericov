package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.application.port.CoverageAnalysisProcessor;
import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.application.port.NormalizedCoverageLocation;
import dev.vericov.analysis.application.port.NormalizedCoverageStore;
import dev.vericov.analysis.application.port.RepositoryContextRepository;
import dev.vericov.analysis.application.port.TestRunRepository;
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
import dev.vericov.analysis.testresults.ParsedTestRun;
import dev.vericov.analysis.testresults.TestResultParserRegistry;
import dev.vericov.analysis.testresults.TestRun;
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
    private final TestResultParserRegistry testResultParserRegistry;
    private final TestRunRepository testRunRepository;
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
                TestResultParserRegistry.empty(),
                TestRunRepository.noop(),
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
                TestResultParserRegistry.empty(),
                TestRunRepository.noop(),
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
            TestResultParserRegistry testResultParserRegistry,
            TestRunRepository testRunRepository,
            Clock clock) {
        this(
                inputs,
                contentStore,
                reports,
                gates,
                emptyRepositoryContextRepository(),
                normalizedCoverageStore,
                parserRegistry,
                testResultParserRegistry,
                testRunRepository,
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
                TestResultParserRegistry.empty(),
                TestRunRepository.noop(),
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
                TestResultParserRegistry.empty(),
                TestRunRepository.noop(),
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
        this(
                inputs,
                contentStore,
                reports,
                gates,
                contextRepository,
                normalizedCoverageStore,
                parserRegistry,
                TestResultParserRegistry.empty(),
                TestRunRepository.noop(),
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
            TestResultParserRegistry testResultParserRegistry,
            TestRunRepository testRunRepository,
            PrDiffCoverageProcessor prDiffCoverageProcessor,
            Clock clock) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.gates = Objects.requireNonNull(gates, "gates");
        this.contextRepository = Objects.requireNonNull(contextRepository, "contextRepository");
        this.normalizedCoverageStore = Objects.requireNonNull(normalizedCoverageStore, "normalizedCoverageStore");
        this.parserRegistry = Objects.requireNonNull(parserRegistry, "parserRegistry");
        this.testResultParserRegistry = Objects.requireNonNull(testResultParserRegistry, "testResultParserRegistry");
        this.testRunRepository = Objects.requireNonNull(testRunRepository, "testRunRepository");
        this.prDiffCoverageProcessor = Objects.requireNonNull(prDiffCoverageProcessor, "prDiffCoverageProcessor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void process(UploadReceivedEvent event) {
        Instant processedAt = clock.instant();
        CoverageAnalysisInput input = inputs.load(event.uploadId());
        List<CoverageInputArtifact> coverageArtifacts = input.coverageArtifacts();
        List<CoverageInputArtifact> testResultArtifacts = input.testResultArtifacts();
        if (coverageArtifacts.isEmpty() && testResultArtifacts.isEmpty()) {
            throw new IllegalStateException("No analyzable artifacts found for upload " + event.uploadId());
        }

        List<ParsedCoverage> parsedCoverages = new ArrayList<>();
        for (CoverageInputArtifact artifact : coverageArtifacts) {
            byte[] content = contentStore.read(artifact.storageBucket(), artifact.storagePath());
            parsedCoverages.add(parserRegistry.parse(artifact, content));
        }
        List<TestRun> testRuns = parseTestRuns(input, testResultArtifacts, processedAt);

        if (!coverageArtifacts.isEmpty()) {
            processCoverage(input, parsedCoverages, processedAt);
        }
        if (!testRuns.isEmpty()) {
            testRunRepository.save(input, testRuns, processedAt);
        }
    }

    private void processCoverage(CoverageAnalysisInput input, List<ParsedCoverage> parsedCoverages, Instant processedAt) {
        CoverageReport report = merger.merge(input, parsedCoverages, processedAt);
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

    private List<TestRun> parseTestRuns(
            CoverageAnalysisInput input,
            List<CoverageInputArtifact> testResultArtifacts,
            Instant processedAt) {
        List<TestRun> runs = new ArrayList<>();
        for (CoverageInputArtifact artifact : testResultArtifacts) {
            byte[] content = contentStore.read(artifact.storageBucket(), artifact.storagePath());
            List<ParsedTestRun> parsedRuns = testResultParserRegistry.parse(artifact, content);
            for (ParsedTestRun parsedRun : parsedRuns) {
                runs.add(new TestRun(
                        java.util.UUID.randomUUID(),
                        input.tenantId(),
                        input.repositoryId(),
                        input.uploadId(),
                        artifact.artifactId(),
                        input.commitSha(),
                        input.branch(),
                        input.pullRequestNumber(),
                        parsedRun.suiteName(),
                        parsedRun.suiteIndex(),
                        parsedRun.status(),
                        parsedRun.totalCount(),
                        parsedRun.passedCount(),
                        parsedRun.failedCount(),
                        parsedRun.errorCount(),
                        parsedRun.skippedCount(),
                        parsedRun.durationMs(),
                        processedAt));
            }
        }
        return List.copyOf(runs);
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
