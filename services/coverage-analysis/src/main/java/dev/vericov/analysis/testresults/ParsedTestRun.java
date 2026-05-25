package dev.vericov.analysis.testresults;

public record ParsedTestRun(
        String suiteName,
        int suiteIndex,
        String status,
        int totalCount,
        int passedCount,
        int failedCount,
        int errorCount,
        int skippedCount,
        long durationMs) {
}
