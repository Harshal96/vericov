package dev.vericov.analysis.adapter.storage;

import dev.vericov.analysis.application.port.NormalizedCoverageLocation;
import dev.vericov.analysis.application.port.NormalizedCoverageStore;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.NormalizedCoverageMapSerializer;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

public class FileSystemNormalizedCoverageStore implements NormalizedCoverageStore {
    private final Path root;
    private final String bucket;
    private final NormalizedCoverageMapSerializer serializer;

    public FileSystemNormalizedCoverageStore(
            Path root,
            String bucket,
            NormalizedCoverageMapSerializer serializer) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.bucket = requireBucket(bucket);
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    @Override
    public NormalizedCoverageLocation store(CoverageReport report) {
        Objects.requireNonNull(report, "report");
        String storagePath = report.tenantId()
                + "/"
                + report.uploadId()
                + "/coverage-normalized/coverage-map.json.gz";
        Path destination = resolve(storagePath);
        writeAtomically(destination, serializer.serialize(report));
        return new NormalizedCoverageLocation(bucket, storagePath);
    }

    private Path resolve(String storagePath) {
        Path resolved = root.resolve(bucket).resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Normalized coverage location escapes the storage root");
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
            throw new IllegalStateException("Failed to store normalized coverage at " + destination, exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The destination write result is authoritative; stale temp files are harmless.
            }
        }
    }

    private static String requireBucket(String bucket) {
        if (bucket == null
                || bucket.isBlank()
                || bucket.contains("/")
                || bucket.contains("\\")
                || ".".equals(bucket)
                || "..".equals(bucket)) {
            throw new IllegalArgumentException("bucket must be a path segment");
        }
        return bucket;
    }
}
