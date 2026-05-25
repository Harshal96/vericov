package dev.vericov.upload.application.port;

import dev.vericov.upload.application.AnalysisJob;
import dev.vericov.upload.application.QueuedUpload;

public interface UploadWorkQueue {
    AnalysisJob enqueueAnalysis(QueuedUpload upload);
}
