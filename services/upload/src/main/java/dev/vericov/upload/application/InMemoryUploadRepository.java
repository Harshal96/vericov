package dev.vericov.upload.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUploadRepository implements dev.vericov.upload.application.port.UploadRepository {
    private final Map<UUID, QueuedUpload> uploadsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> uploadIdsByIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<UUID, List<StoredArtifact>> artifactsByUploadId = new ConcurrentHashMap<>();

    @Override
    public Optional<QueuedUpload> findById(UUID uploadId) {
        return Optional.ofNullable(uploadsById.get(uploadId));
    }

    @Override
    public Optional<QueuedUpload> findByIdempotencyKey(UUID repositoryId, String idempotencyKey) {
        UUID uploadId = uploadIdsByIdempotencyKey.get(idempotencyIndexKey(repositoryId, idempotencyKey));
        if (uploadId == null) {
            return Optional.empty();
        }
        return findById(uploadId);
    }

    @Override
    public void save(QueuedUpload upload, List<StoredArtifact> artifacts) {
        uploadsById.put(upload.uploadId(), upload);
        uploadIdsByIdempotencyKey.put(idempotencyIndexKey(upload.repositoryId(), upload.idempotencyKey()), upload.uploadId());
        artifactsByUploadId.put(upload.uploadId(), List.copyOf(artifacts));
    }

    @Override
    public List<StoredArtifact> artifactsFor(UUID uploadId) {
        return List.copyOf(artifactsByUploadId.getOrDefault(uploadId, new ArrayList<>()));
    }

    private static String idempotencyIndexKey(UUID repositoryId, String idempotencyKey) {
        return repositoryId + ":" + idempotencyKey;
    }
}
