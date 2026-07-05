package dev.vericov.analysis.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnifiedDiffParserTest {
    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    @Test
    void parsesAddedAndDeletedLinesWithZeroContext() {
        String diff = """
                diff --git a/src/Main.java b/src/Main.java
                index abc123..def456 100644
                --- a/src/Main.java
                +++ b/src/Main.java
                @@ -10,2 +10,1 @@
                -old line one
                -old line two
                +new line one
                """;
        PullRequestDiff parsed = parser.parse("base-sha", "head-sha", diff.getBytes(StandardCharsets.UTF_8));

        assertEquals("base-sha", parsed.baseSha());
        assertEquals("head-sha", parsed.headSha());
        assertEquals(1, parsed.files().size());
        PullRequestDiffFile file = parsed.files().get(0);
        assertEquals("src/Main.java", file.filePath());
        assertNull(file.oldFilePath());
        assertEquals("modified", file.changeStatus());
        assertEquals(3, file.lines().size());
        assertEquals(DiffLineType.DELETED, file.lines().get(0).type());
        assertEquals(10, file.lines().get(0).baseLineNumber());
        assertEquals(DiffLineType.ADDED, file.lines().get(2).type());
        assertEquals(10, file.lines().get(2).headLineNumber());
    }

    @Test
    void marksNewFilesAsAdded() {
        String diff = """
                diff --git a/src/New.java b/src/New.java
                new file mode 100644
                index 0000000..abc123
                --- /dev/null
                +++ b/src/New.java
                @@ -0,0 +1,2 @@
                +line one
                +line two
                """;
        PullRequestDiffFile file = parser.parse("base", "head", diff.getBytes(StandardCharsets.UTF_8)).files().get(0);

        assertEquals("added", file.changeStatus());
        assertEquals("src/New.java", file.filePath());
        assertEquals(2, file.lines().size());
    }

    @Test
    void marksRemovedFilesAsDeleted() {
        String diff = """
                diff --git a/src/Old.java b/src/Old.java
                deleted file mode 100644
                index abc123..0000000
                --- a/src/Old.java
                +++ /dev/null
                @@ -1,2 +0,0 @@
                -line one
                -line two
                """;
        PullRequestDiffFile file = parser.parse("base", "head", diff.getBytes(StandardCharsets.UTF_8)).files().get(0);

        assertEquals("deleted", file.changeStatus());
        assertEquals("src/Old.java", file.filePath());
    }

    @Test
    void tracksRenamesWithOldFilePath() {
        String diff = """
                diff --git a/src/Old.java b/src/New.java
                similarity index 100%
                rename from src/Old.java
                rename to src/New.java
                """;
        PullRequestDiffFile file = parser.parse("base", "head", diff.getBytes(StandardCharsets.UTF_8)).files().get(0);

        assertEquals("renamed", file.changeStatus());
        assertEquals("src/New.java", file.filePath());
        assertEquals("src/Old.java", file.oldFilePath());
    }

    @Test
    void handlesMultipleFilesInOneDiff() {
        String diff = """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -1,1 +1,1 @@
                -old
                +new
                diff --git a/b.txt b/b.txt
                --- a/b.txt
                +++ b/b.txt
                @@ -1,1 +1,1 @@
                -old
                +new
                """;
        List<PullRequestDiffFile> files = parser.parse("base", "head", diff.getBytes(StandardCharsets.UTF_8)).files();

        assertEquals(2, files.size());
        assertEquals("a.txt", files.get(0).filePath());
        assertEquals("b.txt", files.get(1).filePath());
    }

    @Test
    void rejectsPathTraversal() {
        String diff = """
                diff --git a/../secret.txt b/../secret.txt
                --- a/../secret.txt
                +++ b/../secret.txt
                @@ -1,1 +1,1 @@
                -old
                +new
                """;
        assertThrows(InvalidUnifiedDiffException.class,
                () -> parser.parse("base", "head", diff.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsOversizedDiffs() {
        byte[] oversized = new byte[UnifiedDiffParser.MAX_BYTES + 1];
        assertThrows(InvalidUnifiedDiffException.class, () -> parser.parse("base", "head", oversized));
    }

    @Test
    void treatsBinaryFilesAsChangesWithoutLines() {
        String diff = """
                diff --git a/image.png b/image.png
                index abc123..def456 100644
                Binary files a/image.png and b/image.png differ
                """;
        PullRequestDiffFile file = parser.parse("base", "head", diff.getBytes(StandardCharsets.UTF_8)).files().get(0);

        assertTrue(file.lines().isEmpty());
    }
}
