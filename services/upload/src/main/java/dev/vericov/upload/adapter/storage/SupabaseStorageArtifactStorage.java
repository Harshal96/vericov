package dev.vericov.upload.adapter.storage;

import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.domain.UploadArtifactInput;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public class SupabaseStorageArtifactStorage implements ArtifactStorage {
    private final SupabaseObjectStorageClient client;
    private final RawArtifactBucketMapping buckets;

    public SupabaseStorageArtifactStorage(
            SupabaseObjectStorageClient client,
            RawArtifactBucketMapping buckets) {
        this.client = Objects.requireNonNull(client, "client");
        this.buckets = Objects.requireNonNull(buckets, "buckets");
    }

    @Override
    public StoredArtifact store(UUID tenantId, UUID uploadId, UploadArtifactInput artifact) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(artifact, "artifact");

        byte[] content = artifact.content();
        String bucket = buckets.bucketFor(artifact.kind());
        String objectPath = tenantId + "/" + uploadId + "/" + artifact.kind().wireValue() + "/" + artifact.name();
        client.upload(bucket, objectPath, artifact.contentType(), content);

        return new StoredArtifact(
                artifact.name(),
                artifact.kind(),
                artifact.format(),
                artifact.contentType(),
                content.length,
                bucket,
                objectPath,
                sha256(content));
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
