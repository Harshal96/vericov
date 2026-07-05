package dev.vericov.upload.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.upload.domain.DiffArtifactValidator.InvalidDiffArtifactException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DiffArtifactValidatorTest {
    @Test
    void acceptsAWellFormedUnifiedDiff() {
        String diff = """
                diff --git a/src/Main.java b/src/Main.java
                index abc123..def456 100644
                --- a/src/Main.java
                +++ b/src/Main.java
                @@ -1,1 +1,1 @@
                -old
                +new
                """;
        assertDoesNotThrow(() -> DiffArtifactValidator.validate(diff.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEmptyContent() {
        assertThrows(InvalidDiffArtifactException.class, () -> DiffArtifactValidator.validate(new byte[0]));
    }

    @Test
    void rejectsContentWithNoFileHeader() {
        assertThrows(
                InvalidDiffArtifactException.class,
                () -> DiffArtifactValidator.validate("not a diff".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsPathTraversalInHeaders() {
        String diff = """
                diff --git a/../secret.txt b/../secret.txt
                --- a/../secret.txt
                +++ b/../secret.txt
                @@ -1,1 +1,1 @@
                -old
                +new
                """;
        assertThrows(
                InvalidDiffArtifactException.class,
                () -> DiffArtifactValidator.validate(diff.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsOversizedContent() {
        byte[] oversized = new byte[DiffArtifactValidator.MAX_BYTES + 1];
        assertThrows(InvalidDiffArtifactException.class, () -> DiffArtifactValidator.validate(oversized));
    }

    @Test
    void acceptsRenamesAndDevNullAndDotSlashPaths() {
        String diff = """
                diff --git a/./src/Old.java b/src/New.java
                similarity index 90%
                rename from src/Old.java
                rename to src/New.java
                --- a/src/Old.java\told-timestamp
                +++ /dev/null
                @@ -1,1 +0,0 @@
                -old
                """;
        assertDoesNotThrow(() -> DiffArtifactValidator.validate(diff.getBytes(StandardCharsets.UTF_8)));
    }
}
