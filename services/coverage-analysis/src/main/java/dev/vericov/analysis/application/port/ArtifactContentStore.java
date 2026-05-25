package dev.vericov.analysis.application.port;

public interface ArtifactContentStore {
    byte[] read(String bucket, String storagePath);
}
