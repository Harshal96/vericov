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
import dev.vericov.analysis.coverage.CoverageComponentRollup;
import dev.vericov.analysis.coverage.CoverageFileFilter;
import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageParserRegistry;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportMerger;
import dev.vericov.analysis.coverage.ParsedCoverage;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import dev.vericov.analysis.domain.NonRetryableAnalysisException;
import dev.vericov.ignore.InvalidCoverageIgnoreRuleException;
import dev.vericov.analysis.gaps.CoverageGapExtractor;
import dev.vericov.analysis.gaps.CoverageGapFinding;
import dev.vericov.analysis.gates.GateEvaluation;
import dev.vericov.analysis.gates.GateEvaluator;
import dev.vericov.analysis.gates.ComponentRollup;
import dev.vericov.analysis.gates.Finding;
import dev.vericov.analysis.gates.RepositoryContext;
import dev.vericov.analysis.gates.RepositoryComponentContext;
import dev.vericov.analysis.gates.RepositoryOwnerRuleContext;
import dev.vericov.analysis.gates.RepositoryPackageNodeContext;
import dev.vericov.analysis.testresults.ParsedTestRun;
import dev.vericov.analysis.testresults.TestResultParserRegistry;
import dev.vericov.analysis.testresults.TestRun;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private final CoverageGapExtractor gapExtractor = new CoverageGapExtractor();
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
        CoverageFileFilter coverageFileFilter;
        try {
            coverageFileFilter = new CoverageFileFilter(input.ignore());
        } catch (InvalidCoverageIgnoreRuleException exception) {
            throw new NonRetryableAnalysisException(
                    "Invalid persisted upload ignore rules: " + exception.getMessage(),
                    exception);
        }
        List<CoverageInputArtifact> coverageArtifacts = input.coverageArtifacts();
        List<CoverageInputArtifact> testResultArtifacts = input.testResultArtifacts();
        if (coverageArtifacts.isEmpty() && testResultArtifacts.isEmpty()) {
            throw new IllegalStateException("No analyzable artifacts found for upload " + event.uploadId());
        }

        List<ParsedCoverage> parsedCoverages = new ArrayList<>();
        for (CoverageInputArtifact artifact : coverageArtifacts) {
            byte[] content = contentStore.read(artifact.storageBucket(), artifact.storagePath());
            parsedCoverages.add(coverageFileFilter.filter(parserRegistry.parse(artifact, content)));
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
        RepositoryContext repositoryContext = contextRepository.loadContext(
                report.tenantId(),
                report.repositoryId(),
                report.commitSha(),
                report.branchName(),
                report.pullRequestNumber());
        CoverageReport contextualReport = applyRepositoryContext(report, repositoryContext);

        NormalizedCoverageLocation location = normalizedCoverageStore.store(contextualReport);
        CoverageReport reportWithStorage = contextualReport.withNormalizedStorage(location.bucket(), location.path());

        // 1. Process diff coverage first so we have the DiffCoverageReport
        DiffCoverageReport diffCoverage = prDiffCoverageProcessor.process(input, reportWithStorage);
        List<CoverageGapFinding> gapFindings = gapExtractor.extract(
                reportWithStorage,
                repositoryContext,
                diffCoverage,
                reportWithStorage.generatedAt());
        CoverageReport reportWithFindings = reportWithStorage
                .withGapFindings(gapFindings)
                .withComponentRollups(coverageComponentRollups(reportWithStorage.files(), gapFindings));
        RepositoryContext gateContext = repositoryContext
                .withComponentRollups(gateComponentRollups(reportWithFindings))
                .withFindings(gateFindings(gapFindings));

        // 3. Evaluate gates with report, context, and diff coverage
        List<GateEvaluation> evaluations = gateEvaluator.evaluate(
                reportWithFindings,
                gates.listActiveForRepository(reportWithFindings.tenantId(), reportWithFindings.repositoryId()),
                gateContext,
                diffCoverage,
                reportWithFindings.generatedAt());

        // 4. Save report and evaluations
        reports.save(reportWithFindings, evaluations);
    }

    private CoverageReport applyRepositoryContext(CoverageReport report, RepositoryContext context) {
        if (context.components().isEmpty() && context.ownerRules().isEmpty() && context.packageNodes().isEmpty()) {
            return report;
        }
        List<CoverageFileSummary> resolvedFiles = report.files().stream()
                .map(file -> resolveFile(file, context))
                .toList();
        return report.withResolvedFiles(resolvedFiles)
                .withComponentRollups(coverageComponentRollups(resolvedFiles, List.of()));
    }

    private static CoverageFileSummary resolveFile(CoverageFileSummary file, RepositoryContext context) {
        RepositoryComponentContext component = matchingComponent(context.components(), file.filePath()).orElse(null);
        RepositoryOwnerRuleContext ownerRule = component == null ? matchingOwnerRule(context.ownerRules(), file.filePath()).orElse(null) : null;
        RepositoryPackageNodeContext packageNode = matchingPackageNode(context.packageNodes(), file.filePath()).orElse(null);
        List<String> owners = component != null
                ? component.owners()
                : ownerRule != null ? ownerRule.owners() : List.of("unowned");
        if (owners.isEmpty()) {
            owners = List.of("unowned");
        }
        return new CoverageFileSummary(
                file.filePath(),
                file.line(),
                file.branch(),
                file.function(),
                file.statement(),
                component == null ? null : component.componentId(),
                packageNode == null ? null : packageNode.packageName(),
                owners);
    }

    private static Optional<RepositoryComponentContext> matchingComponent(
            List<RepositoryComponentContext> components,
            String filePath) {
        return components.stream()
                .filter(component -> component.pathPatterns().stream().anyMatch(pattern -> globMatches(pattern, filePath)))
                .min(Comparator.comparingInt((RepositoryComponentContext component) -> longestLiteralPrefix(
                                component.pathPatterns(),
                                filePath))
                        .reversed()
                        .thenComparing(RepositoryComponentContext::name)
                        .thenComparing(RepositoryComponentContext::componentId));
    }

    private static Optional<RepositoryOwnerRuleContext> matchingOwnerRule(
            List<RepositoryOwnerRuleContext> ownerRules,
            String filePath) {
        return ownerRules.stream()
                .filter(rule -> globMatches(rule.pattern(), filePath))
                .min(Comparator.comparingInt(DefaultCoverageAnalysisProcessor::ownerRuleSourceRank)
                        .thenComparingInt(DefaultCoverageAnalysisProcessor::ownerRulePriorityRank));
    }

    private static int ownerRuleSourceRank(RepositoryOwnerRuleContext ownerRule) {
        return switch (ownerRule.source()) {
            case "manual" -> 0;
            case "component" -> 1;
            case "codeowners" -> 2;
            default -> 3;
        };
    }

    private static int ownerRulePriorityRank(RepositoryOwnerRuleContext ownerRule) {
        return "codeowners".equals(ownerRule.source()) ? -ownerRule.priority() : ownerRule.priority();
    }

    private static Optional<RepositoryPackageNodeContext> matchingPackageNode(
            List<RepositoryPackageNodeContext> packageNodes,
            String filePath) {
        return packageNodes.stream()
                .filter(node -> pathStartsWith(filePath, node.packagePath()))
                .max(Comparator.comparingInt((RepositoryPackageNodeContext node) -> node.packagePath().length())
                        .thenComparing(RepositoryPackageNodeContext::packageName));
    }

    private static List<CoverageComponentRollup> coverageComponentRollups(
            List<CoverageFileSummary> files,
            List<CoverageGapFinding> findings) {
        Map<String, RollupAccumulator> accumulators = new LinkedHashMap<>();
        for (CoverageFileSummary file : files) {
            if (file.componentId() == null) {
                continue;
            }
            String owner = file.owners().isEmpty() ? "unowned" : file.owners().getFirst();
            String key = file.componentId() + ":" + owner;
            accumulators.computeIfAbsent(key, ignored -> new RollupAccumulator(file.componentId(), owner))
                    .add(file);
        }
        for (CoverageGapFinding finding : findings) {
            if (finding.componentId() == null) {
                continue;
            }
            String owner = finding.owners().isEmpty() ? "unowned" : finding.owners().getFirst();
            String key = finding.componentId() + ":" + owner;
            accumulators.computeIfAbsent(key, ignored -> new RollupAccumulator(finding.componentId(), owner))
                    .add(finding);
        }
        return accumulators.values().stream()
                .map(RollupAccumulator::toCoverageRollup)
                .toList();
    }

    private static List<Finding> gateFindings(List<CoverageGapFinding> gapFindings) {
        return gapFindings.stream()
                .map(finding -> new Finding(
                        finding.id(),
                        finding.filePath(),
                        finding.lineStart(),
                        finding.riskLevel(),
                        finding.status()))
                .toList();
    }

    private static Map<String, ComponentRollup> gateComponentRollups(CoverageReport report) {
        Map<String, RollupAccumulator> accumulators = new LinkedHashMap<>();
        for (CoverageComponentRollup rollup : report.componentRollups()) {
            accumulators.computeIfAbsent(
                            rollup.componentId().toString(),
                            ignored -> new RollupAccumulator(rollup.componentId(), rollup.owner()))
                    .add(rollup);
        }
        Map<String, ComponentRollup> rollups = new LinkedHashMap<>();
        accumulators.forEach((componentId, accumulator) -> {
            CoverageComponentRollup rollup = accumulator.toCoverageRollup();
            rollups.put(componentId, new ComponentRollup(
                    rollup.componentId().toString(),
                    rollup.line(),
                    rollup.branch(),
                    rollup.function(),
                    rollup.statement()));
        });
        return Map.copyOf(rollups);
    }

    private static boolean globMatches(String pattern, String filePath) {
        return Pattern.compile(globRegex(pattern)).matcher(filePath).matches();
    }

    private static String globRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        if (!pattern.contains("/")) {
            regex.append("(?:.*/)?");
        }
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '*') {
                boolean doublestar = i + 1 < pattern.length() && pattern.charAt(i + 1) == '*';
                if (doublestar) {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else {
                if (".[]{}()+-^$|".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        return regex.append('$').toString();
    }

    private static int longestLiteralPrefix(List<String> patterns, String filePath) {
        return patterns.stream()
                .filter(pattern -> globMatches(pattern, filePath))
                .mapToInt(DefaultCoverageAnalysisProcessor::literalPrefixLength)
                .max()
                .orElse(0);
    }

    private static int literalPrefixLength(String pattern) {
        int wildcard = pattern.length();
        for (char marker : new char[] {'*', '?'}) {
            int index = pattern.indexOf(marker);
            if (index >= 0) {
                wildcard = Math.min(wildcard, index);
            }
        }
        return pattern.substring(0, wildcard).length();
    }

    private static boolean pathStartsWith(String filePath, String packagePath) {
        return filePath.equals(packagePath) || filePath.startsWith(packagePath + "/");
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

    private static final class RollupAccumulator {
        private final UUID componentId;
        private final String owner;
        private int lineCovered;
        private int lineTotal;
        private int branchCovered;
        private int branchTotal;
        private int functionCovered;
        private int functionTotal;
        private int statementCovered;
        private int statementTotal;
        private int gapCount;
        private int debtCount;
        private BigDecimal riskScoreTotal = BigDecimal.ZERO;
        private String highestActiveRiskLevel;

        private RollupAccumulator(UUID componentId, String owner) {
            this.componentId = componentId;
            this.owner = owner;
        }

        private void add(CoverageFileSummary file) {
            lineCovered += file.line().covered();
            lineTotal += file.line().total();
            branchCovered += file.branch().covered();
            branchTotal += file.branch().total();
            functionCovered += file.function().covered();
            functionTotal += file.function().total();
            statementCovered += file.statement().covered();
            statementTotal += file.statement().total();
        }

        private void add(CoverageComponentRollup rollup) {
            lineCovered += rollup.line().covered();
            lineTotal += rollup.line().total();
            branchCovered += rollup.branch().covered();
            branchTotal += rollup.branch().total();
            functionCovered += rollup.function().covered();
            functionTotal += rollup.function().total();
            statementCovered += rollup.statement().covered();
            statementTotal += rollup.statement().total();
            gapCount += rollup.gapCount();
            debtCount += rollup.debtCount();
            riskScoreTotal = riskScoreTotal.add(rollup.riskScoreTotal());
            highestActiveRiskLevel = higherRiskLevel(highestActiveRiskLevel, rollup.highestActiveRiskLevel());
        }

        private void add(CoverageGapFinding finding) {
            if ("debt_suppressed".equals(finding.status())) {
                debtCount++;
            } else if ("active".equals(finding.status())) {
                gapCount++;
                riskScoreTotal = riskScoreTotal.add(finding.riskScore());
                highestActiveRiskLevel = higherRiskLevel(highestActiveRiskLevel, finding.riskLevel());
            }
        }

        private CoverageComponentRollup toCoverageRollup() {
            return new CoverageComponentRollup(
                    componentId,
                    owner,
                    new CoverageMetric(lineCovered, lineTotal),
                    new CoverageMetric(branchCovered, branchTotal),
                    new CoverageMetric(functionCovered, functionTotal),
                    new CoverageMetric(statementCovered, statementTotal),
                    gapCount,
                    debtCount,
                    riskScoreTotal,
                    highestActiveRiskLevel);
        }

        private static String higherRiskLevel(String current, String candidate) {
            if (candidate == null) {
                return current;
            }
            if (current == null || riskRank(candidate) > riskRank(current)) {
                return candidate;
            }
            return current;
        }

        private static int riskRank(String level) {
            return switch (level) {
                case "critical" -> 4;
                case "high" -> 3;
                case "medium" -> 2;
                case "low" -> 1;
                default -> 0;
            };
        }
    }

    private static RepositoryContextRepository emptyRepositoryContextRepository() {
        return (tenantId, repositoryId, commitSha, branch, pr) -> new RepositoryContext(
                "ctx-" + Instant.now().toString(),
                List.of(),
                List.of(),
                Map.of());
    }
}
