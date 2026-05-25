package dev.vericov.analysis.domain;

public record AnalysisFailureDecision(AnalysisFailureAction action) {

    public static AnalysisFailureDecision retryLater() {
        return new AnalysisFailureDecision(AnalysisFailureAction.RETRY_LATER);
    }

    public static AnalysisFailureDecision deadLetter() {
        return new AnalysisFailureDecision(AnalysisFailureAction.DEAD_LETTER);
    }

    public boolean shouldDeadLetter() {
        return action == AnalysisFailureAction.DEAD_LETTER;
    }
}
