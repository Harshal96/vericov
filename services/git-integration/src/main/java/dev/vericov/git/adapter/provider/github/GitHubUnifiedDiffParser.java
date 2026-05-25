package dev.vericov.git.adapter.provider.github;

import dev.vericov.git.application.GitDiffLineDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubUnifiedDiffParser {
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");

    public List<GitDiffLineDetails> parse(String patch) {
        if (patch == null || patch.isBlank()) {
            return List.of();
        }
        List<GitDiffLineDetails> lines = new ArrayList<>();
        int baseLineNumber = 0;
        int headLineNumber = 0;
        boolean inHunk = false;
        for (String rawLine : patch.split("\\R")) {
            Matcher hunk = HUNK_HEADER.matcher(rawLine);
            if (hunk.matches()) {
                baseLineNumber = Integer.parseInt(hunk.group(1));
                headLineNumber = Integer.parseInt(hunk.group(2));
                inHunk = true;
                continue;
            }
            if (!inHunk || rawLine.startsWith("\\ No newline at end of file")) {
                continue;
            }
            if (rawLine.startsWith("+")) {
                lines.add(new GitDiffLineDetails(null, headLineNumber, "added"));
                headLineNumber++;
                continue;
            }
            if (rawLine.startsWith("-")) {
                lines.add(new GitDiffLineDetails(baseLineNumber, null, "deleted"));
                baseLineNumber++;
                continue;
            }
            if (rawLine.startsWith(" ")) {
                lines.add(new GitDiffLineDetails(baseLineNumber, headLineNumber, "context"));
                baseLineNumber++;
                headLineNumber++;
            }
        }
        return List.copyOf(lines);
    }
}
