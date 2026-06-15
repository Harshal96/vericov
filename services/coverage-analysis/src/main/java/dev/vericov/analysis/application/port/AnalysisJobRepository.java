package dev.vericov.analysis.application.port;

import dev.vericov.analysis.domain.AnalysisFailureDecision;
import dev.vericov.analysis.domain.AnalysisJobStartResult;
import java.time.Instant;
import java.util.UUID;

public interface AnalysisJobRepository {
    AnalysisJobStartResult startJob(UUID jobId, String workerId, Instant startedAt);

    void completeJob(UUID jobId, Instant finishedAt);

    AnalysisFailureDecision recordFailure(UUID jobId, String workerId, Instant failedAt, String errorMessage);

    void recordTerminalFailure(UUID jobId, String workerId, Instant failedAt, String errorMessage);
}
