package dev.vericov.upload.application.port;

import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.StoredArtifact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadRepository {
    Optional<QueuedUpload> findById(UUID uploadId);

    Optional<QueuedUpload> findByIdempotencyKey(UUID repositoryId, String idempotencyKey);

    void save(QueuedUpload upload, List<StoredArtifact> artifacts);

    List<StoredArtifact> artifactsFor(UUID uploadId);
}
