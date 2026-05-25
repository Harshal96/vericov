package dev.vericov.analysis.domain;

public record AnalysisJobStartResult(AnalysisJobStartStatus status) {

    public static AnalysisJobStartResult started() {
        return new AnalysisJobStartResult(AnalysisJobStartStatus.STARTED);
    }

    public static AnalysisJobStartResult busy() {
        return new AnalysisJobStartResult(AnalysisJobStartStatus.BUSY);
    }

    public static AnalysisJobStartResult alreadyFinished() {
        return new AnalysisJobStartResult(AnalysisJobStartStatus.ALREADY_FINISHED);
    }

    public boolean shouldProcess() {
        return status == AnalysisJobStartStatus.STARTED;
    }

    public boolean shouldRetryClaimLater() {
        return status == AnalysisJobStartStatus.BUSY;
    }
}
