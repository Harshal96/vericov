package dev.vericov.analysis.application.port;

import dev.vericov.analysis.domain.UploadReceivedEvent;

public interface CoverageAnalysisProcessor {
    void process(UploadReceivedEvent event);
}
