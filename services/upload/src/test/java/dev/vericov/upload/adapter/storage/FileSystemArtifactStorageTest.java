package dev.vericov.upload.adapter.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.UploadArtifactInput;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemArtifactStorageTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UPLOAD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir
    Path root;

    @Test
    void storesArtifactInsideBucketAndReturnsPortableLocation() throws Exception {
        byte[] content = "TN:\nend_of_record\n".getBytes(StandardCharsets.UTF_8);
        var stored = new FileSystemArtifactStorage(root).store(
                TENANT_ID,
                UPLOAD_ID,
                new UploadArtifactInput(
                        "coverage.lcov",
                        ArtifactKind.COVERAGE,
                        "lcov",
                        "text/plain",
                        content));

        assertEquals("coverage-raw", stored.storageBucket());
        assertEquals(
                TENANT_ID + "/" + UPLOAD_ID + "/coverage/coverage.lcov",
                stored.storagePath());
        assertArrayEquals(
                content,
                Files.readAllBytes(root.resolve(stored.storageBucket()).resolve(stored.storagePath())));
    }

    @Test
    void rejectsArtifactNamesThatCouldEscapeStorageRoot() {
        var artifact = new UploadArtifactInput(
                "../coverage.lcov",
                ArtifactKind.COVERAGE,
                "lcov",
                "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));

        assertThrows(
                IllegalArgumentException.class,
                () -> new FileSystemArtifactStorage(root).store(TENANT_ID, UPLOAD_ID, artifact));
    }
}
