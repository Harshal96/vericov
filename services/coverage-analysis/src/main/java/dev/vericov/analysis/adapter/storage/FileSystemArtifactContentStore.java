package dev.vericov.analysis.adapter.storage;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class FileSystemArtifactContentStore implements ArtifactContentStore {
    private final Path root;

    public FileSystemArtifactContentStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public byte[] read(String bucket, String storagePath) {
        Path artifact = resolve(bucket, storagePath);
        try {
            return Files.readAllBytes(artifact);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read artifact at " + artifact, exception);
        }
    }

    private Path resolve(String bucket, String storagePath) {
        requireRelativePath(bucket, "bucket");
        requireRelativePath(storagePath, "storagePath");
        Path resolved = root.resolve(bucket).resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Artifact location escapes the storage root");
        }
        return resolved;
    }

    private static void requireRelativePath(String value, String name) {
        if (value == null || value.isBlank() || Path.of(value).isAbsolute()) {
            throw new IllegalArgumentException(name + " must be a relative path");
        }
    }
}
