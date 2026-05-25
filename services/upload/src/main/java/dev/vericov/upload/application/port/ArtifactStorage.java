package dev.vericov.upload.application.port;

import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.domain.UploadArtifactInput;
import java.util.UUID;

public interface ArtifactStorage {
    StoredArtifact store(UUID tenantId, UUID uploadId, UploadArtifactInput artifact);
}
