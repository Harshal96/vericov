package dev.vericov.analysis.application;

import dev.vericov.analysis.domain.QueuedAnalysisMessage;

public interface AnalysisMessageHandler {
    void handle(QueuedAnalysisMessage message);
}
