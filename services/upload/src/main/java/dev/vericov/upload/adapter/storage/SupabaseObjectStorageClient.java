package dev.vericov.upload.adapter.storage;

public interface SupabaseObjectStorageClient {
    void upload(String bucket, String objectPath, String contentType, byte[] content);
}
