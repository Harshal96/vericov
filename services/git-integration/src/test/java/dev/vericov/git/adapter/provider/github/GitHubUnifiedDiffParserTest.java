package dev.vericov.git.adapter.provider.github;

import dev.vericov.git.application.GitDiffLineDetails;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubUnifiedDiffParserTest {
    @Test
    void parsesHunkLineNumbersWithoutPersistingSourceText() {
        List<GitDiffLineDetails> lines = new GitHubUnifiedDiffParser().parse("""
                @@ -7,2 +7,3 @@
                 unchanged
                -removed
                +added one
                +added two
                """);

        assertEquals(List.of(
                        new GitDiffLineDetails(7, 7, "context"),
                        new GitDiffLineDetails(8, null, "deleted"),
                        new GitDiffLineDetails(null, 8, "added"),
                        new GitDiffLineDetails(null, 9, "added")),
                lines);
    }
}
