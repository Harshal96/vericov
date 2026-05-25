package dev.vericov.upload.adapter.storage;

import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.UploadArtifactInput;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SupabaseStorageArtifactStorageTest {
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");

    @Test
    void storesCoverageArtifactInRawCoverageBucket() {
        RecordingObjectStorageClient client = new RecordingObjectStorageClient();
        SupabaseStorageArtifactStorage storage = new SupabaseStorageArtifactStorage(
                client,
                new RawArtifactBucketMapping("coverage-raw", "test-results-raw", "metadata-raw"));
        byte[] content = "TN:\nSF:src/Main.java\n".getBytes(StandardCharsets.UTF_8);

        StoredArtifact stored = storage.store(
                TENANT_ID,
                UPLOAD_ID,
                new UploadArtifactInput(
                        "lcov.info",
                        ArtifactKind.COVERAGE,
                        "lcov",
                        "text/plain",
                        content));

        assertEquals("coverage-raw", stored.storageBucket());
        assertEquals(TENANT_ID + "/" + UPLOAD_ID + "/coverage/lcov.info", stored.storagePath());
        assertEquals("lcov.info", stored.name());
        assertEquals(content.length, stored.sizeBytes());
        assertEquals(sha256(content), stored.sha256());
        assertEquals(1, client.uploads.size());
        ObjectUpload upload = client.uploads.getFirst();
        assertEquals("coverage-raw", upload.bucket());
        assertEquals(TENANT_ID + "/" + UPLOAD_ID + "/coverage/lcov.info", upload.objectPath());
        assertEquals("text/plain", upload.contentType());
        assertArrayEquals(content, upload.content());
    }

    @Test
    void routesTestResultArtifactsToTestResultsBucket() {
        RecordingObjectStorageClient client = new RecordingObjectStorageClient();
        SupabaseStorageArtifactStorage storage = new SupabaseStorageArtifactStorage(
                client,
                new RawArtifactBucketMapping("coverage-raw", "test-results-raw", "metadata-raw"));

        StoredArtifact stored = storage.store(
                TENANT_ID,
                UPLOAD_ID,
                new UploadArtifactInput(
                        "junit.xml",
                        ArtifactKind.TEST_RESULTS,
                        "junit",
                        "application/xml",
                        "<testsuite/>".getBytes(StandardCharsets.UTF_8)));

        assertEquals("test-results-raw", stored.storageBucket());
        assertEquals(TENANT_ID + "/" + UPLOAD_ID + "/test_results/junit.xml", stored.storagePath());
        assertEquals("test-results-raw", client.uploads.getFirst().bucket());
    }

    @Test
    void buildsStorageUploadUriWithEncodedPathSegments() {
        HttpSupabaseObjectStorageClient client = new HttpSupabaseObjectStorageClient(
                URI.create("http://localhost:8000/storage/v1"),
                "service-role-key");

        URI uri = client.uploadUri("coverage-raw", "tenant-id/upload-id/coverage/lcov report.info");

        assertEquals(
                URI.create("http://localhost:8000/storage/v1/object/coverage-raw/tenant-id/upload-id/coverage/lcov%20report.info"),
                uri);
    }

    private static String sha256(byte[] content) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class RecordingObjectStorageClient implements SupabaseObjectStorageClient {
        private final List<ObjectUpload> uploads = new ArrayList<>();

        @Override
        public void upload(String bucket, String objectPath, String contentType, byte[] content) {
            uploads.add(new ObjectUpload(bucket, objectPath, contentType, content));
        }
    }

    private record ObjectUpload(
            String bucket,
            String objectPath,
            String contentType,
            byte[] content) {
    }
}
