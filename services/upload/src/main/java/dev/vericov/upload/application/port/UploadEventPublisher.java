package dev.vericov.upload.application.port;

import dev.vericov.upload.application.UploadEvent;

public interface UploadEventPublisher {
    void publish(UploadEvent event);
}
