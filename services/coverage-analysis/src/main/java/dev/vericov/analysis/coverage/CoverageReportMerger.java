package dev.vericov.analysis.coverage;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CoverageReportMerger {

    public CoverageReport merge(CoverageAnalysisInput input, List<ParsedCoverage> parsedCoverages, Instant generatedAt) {
        Map<String, FileAccumulator> files = new HashMap<>();
        for (ParsedCoverage parsedCoverage : parsedCoverages) {
            for (ParsedCoverageFile file : parsedCoverage.files()) {
                files.computeIfAbsent(file.filePath(), FileAccumulator::new).add(file);
            }
        }

        List<ParsedCoverageFile> mergedFiles = files.values().stream()
                .map(FileAccumulator::toParsedFile)
                .sorted(Comparator.comparing(ParsedCoverageFile::filePath))
                .toList();
        CoverageMetric line = sumMetric(mergedFiles.stream().map(ParsedCoverageFile::line).toList());
        CoverageMetric branch = sumMetric(mergedFiles.stream().map(ParsedCoverageFile::branch).toList());
        CoverageMetric function = sumMetric(mergedFiles.stream().map(ParsedCoverageFile::function).toList());
        CoverageMetric statement = sumMetric(mergedFiles.stream().map(ParsedCoverageFile::statement).toList());

        List<CoverageFileSummary> summaries = mergedFiles.stream()
                .map(file -> new CoverageFileSummary(
                        file.filePath(),
                        file.line(),
                        file.branch(),
                        file.function(),
                        file.statement()))
                .toList();
        List<CoverageLineHit> lineHits = mergedFiles.stream()
                .flatMap(file -> file.lineHits().entrySet().stream()
                        .map(entry -> new CoverageLineHit(file.filePath(), entry.getKey(), entry.getValue())))
                .sorted(Comparator.comparing(CoverageLineHit::filePath)
                        .thenComparingInt(CoverageLineHit::lineNumber))
                .toList();

        return new CoverageReport(
                UUID.randomUUID(),
                input.uploadId(),
                input.tenantId(),
                input.repositoryId(),
                input.commitSha(),
                input.branch(),
                input.pullRequestNumber(),
                line,
                branch,
                function,
                statement,
                summaries,
                lineHits,
                generatedAt);
    }

    private static CoverageMetric sumMetric(List<CoverageMetric> metrics) {
        int covered = metrics.stream().mapToInt(CoverageMetric::covered).sum();
        int total = metrics.stream().mapToInt(CoverageMetric::total).sum();
        return new CoverageMetric(covered, total);
    }

    private static final class FileAccumulator {
        private final String filePath;
        private final Map<Integer, Long> lineHits = new HashMap<>();
        private final Set<String> branches = new HashSet<>();
        private final Set<String> coveredBranches = new HashSet<>();
        private final Set<String> functions = new HashSet<>();
        private final Set<String> coveredFunctions = new HashSet<>();
        private final Set<String> statements = new HashSet<>();
        private final Set<String> coveredStatements = new HashSet<>();

        private FileAccumulator(String filePath) {
            this.filePath = filePath;
        }

        private void add(ParsedCoverageFile file) {
            file.lineHits().forEach((lineNumber, hits) -> lineHits.merge(lineNumber, hits, Long::sum));
            branches.addAll(file.branches());
            coveredBranches.addAll(file.coveredBranches());
            functions.addAll(file.functions());
            coveredFunctions.addAll(file.coveredFunctions());
            statements.addAll(file.statements());
            coveredStatements.addAll(file.coveredStatements());
        }

        private ParsedCoverageFile toParsedFile() {
            return new ParsedCoverageFile(
                    filePath,
                    lineHits,
                    branches,
                    coveredBranches,
                    functions,
                    coveredFunctions,
                    statements,
                    coveredStatements);
        }
    }
}
