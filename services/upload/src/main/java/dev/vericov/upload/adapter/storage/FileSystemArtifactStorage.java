package dev.vericov.upload.adapter.storage;

import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.domain.UploadArtifactInput;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public class FileSystemArtifactStorage implements ArtifactStorage {
    private final Path root;
    private final RawArtifactBucketMapping buckets;

    public FileSystemArtifactStorage(Path root) {
        this(
                root,
                new RawArtifactBucketMapping("coverage-raw", "test-results-raw", "metadata-raw"));
    }

    public FileSystemArtifactStorage(Path root, RawArtifactBucketMapping buckets) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.buckets = Objects.requireNonNull(buckets, "buckets");
    }

    @Override
    public StoredArtifact store(UUID tenantId, UUID uploadId, UploadArtifactInput artifact) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(artifact, "artifact");
        requireFileName(artifact.name());

        String bucket = buckets.bucketFor(artifact.kind());
        String storagePath = tenantId + "/" + uploadId + "/" + artifact.kind().wireValue() + "/" + artifact.name();
        byte[] content = artifact.content();
        writeAtomically(resolve(bucket, storagePath), content);
        return new StoredArtifact(
                artifact.name(),
                artifact.kind(),
                artifact.format(),
                artifact.contentType(),
                content.length,
                bucket,
                storagePath,
                sha256(content));
    }

    private Path resolve(String bucket, String storagePath) {
        requirePathSegment(bucket, "bucket");
        Path resolved = root.resolve(bucket).resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Artifact location escapes the storage root");
        }
        return resolved;
    }

    private static void writeAtomically(Path destination, byte[] content) {
        Path temporary = destination.resolveSibling(destination.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(destination.getParent());
            Files.write(temporary, content);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store artifact at " + destination, exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The destination write result is authoritative; stale temp files are harmless.
            }
        }
    }

    private static void requireFileName(String fileName) {
        if (fileName == null
                || fileName.isBlank()
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("..")) {
            throw new IllegalArgumentException("artifact name must be a file name");
        }
    }

    private static void requirePathSegment(String value, String name) {
        if (value == null
                || value.isBlank()
                || value.contains("/")
                || value.contains("\\")
                || ".".equals(value)
                || "..".equals(value)) {
            throw new IllegalArgumentException(name + " must be a path segment");
        }
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
