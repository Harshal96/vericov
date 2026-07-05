package dev.vericov.analysis.diff;

import dev.vericov.analysis.coverage.CoveragePath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a {@code git diff --unified=0 --find-renames} unified diff into the
 * {@link PullRequestDiff} model. Untrusted input: bounded by file/line/byte
 * limits enforced identically in the Upload Service.
 */
public final class UnifiedDiffParser {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_FILES = 10_000;
    public static final int MAX_CHANGED_LINES = 500_000;

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");
    private static final Pattern DIFF_GIT_HEADER = Pattern.compile("^diff --git a/(.+) b/(.+)$");

    public PullRequestDiff parse(String baseSha, String headSha, byte[] content) {
        if (content.length > MAX_BYTES) {
            throw new InvalidUnifiedDiffException("diff exceeds the " + MAX_BYTES + " byte limit");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        List<PullRequestDiffFile> files = new ArrayList<>();
        int changedLines = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line = reader.readLine();
            FileBuilder current = null;
            while (line != null) {
                if (line.startsWith("diff --git ")) {
                    if (current != null) {
                        files.add(current.build());
                    }
                    if (files.size() >= MAX_FILES) {
                        throw new InvalidUnifiedDiffException("diff touches more than " + MAX_FILES + " files");
                    }
                    current = new FileBuilder();
                    Matcher header = DIFF_GIT_HEADER.matcher(line);
                    if (header.matches()) {
                        current.oldPath = validatedPath(header.group(1));
                        current.newPath = validatedPath(header.group(2));
                    }
                } else if (current != null && line.startsWith("rename from ")) {
                    current.renameFrom = validatedPath(line.substring("rename from ".length()));
                } else if (current != null && line.startsWith("rename to ")) {
                    current.renameTo = validatedPath(line.substring("rename to ".length()));
                } else if (current != null && line.startsWith("--- ")) {
                    current.oldPath = pathOrNull(line.substring(4));
                } else if (current != null && line.startsWith("+++ ")) {
                    current.newPath = pathOrNull(line.substring(4));
                } else if (current != null && line.startsWith("Binary files ")) {
                    current.binary = true;
                } else if (current != null && HUNK_HEADER.matcher(line).matches()) {
                    Matcher matcher = HUNK_HEADER.matcher(line);
                    matcher.matches();
                    current.oldLine = Integer.parseInt(matcher.group(1));
                    current.newLine = Integer.parseInt(matcher.group(3));
                    current.inHunk = true;
                } else if (current != null && current.inHunk && !current.binary
                        && !line.isEmpty()
                        && (line.charAt(0) == ' ' || line.charAt(0) == '+' || line.charAt(0) == '-')
                        && !line.startsWith("+++") && !line.startsWith("---")) {
                    changedLines++;
                    if (changedLines > MAX_CHANGED_LINES) {
                        throw new InvalidUnifiedDiffException("diff exceeds " + MAX_CHANGED_LINES + " changed lines");
                    }
                    current.addLine(line);
                }
                line = reader.readLine();
            }
            if (current != null) {
                files.add(current.build());
            }
        } catch (IOException exception) {
            throw new InvalidUnifiedDiffException("failed to read diff content", exception);
        }
        return new PullRequestDiff(baseSha, headSha, files);
    }

    private static String pathOrNull(String rawPath) {
        String trimmed = rawPath.trim();
        int tabIndex = trimmed.indexOf('\t');
        if (tabIndex >= 0) {
            trimmed = trimmed.substring(0, tabIndex);
        }
        if ("/dev/null".equals(trimmed)) {
            return null;
        }
        return validatedPath(trimmed);
    }

    private static String validatedPath(String rawPath) {
        String stripped = rawPath.startsWith("a/") || rawPath.startsWith("b/")
                ? rawPath.substring(2)
                : rawPath;
        String normalized = CoveragePath.normalize(stripped);
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.contains("/../")
                || normalized.endsWith("/..")) {
            throw new InvalidUnifiedDiffException("diff contains an invalid file path: " + rawPath);
        }
        return normalized;
    }

    private static final class FileBuilder {
        private String oldPath;
        private String newPath;
        private String renameFrom;
        private String renameTo;
        private boolean binary;
        private boolean inHunk;
        private int oldLine;
        private int newLine;
        private final List<PullRequestDiffLine> lines = new ArrayList<>();

        private void addLine(String rawLine) {
            char marker = rawLine.charAt(0);
            if (marker == ' ') {
                lines.add(new PullRequestDiffLine(oldLine, newLine, DiffLineType.CONTEXT));
                oldLine++;
                newLine++;
            } else if (marker == '-') {
                lines.add(new PullRequestDiffLine(oldLine, null, DiffLineType.DELETED));
                oldLine++;
            } else if (marker == '+') {
                lines.add(new PullRequestDiffLine(null, newLine, DiffLineType.ADDED));
                newLine++;
            }
        }

        private PullRequestDiffFile build() {
            String effectiveOldPath = renameFrom != null ? renameFrom : oldPath;
            String effectiveNewPath = renameTo != null ? renameTo : newPath;
            String filePath = effectiveNewPath != null ? effectiveNewPath : effectiveOldPath;
            if (filePath == null) {
                throw new InvalidUnifiedDiffException("diff entry has no resolvable file path");
            }
            String changeStatus;
            String oldFilePath;
            if (renameFrom != null) {
                changeStatus = "renamed";
                oldFilePath = renameFrom.equals(filePath) ? null : renameFrom;
            } else if (effectiveOldPath == null) {
                changeStatus = "added";
                oldFilePath = null;
            } else if (effectiveNewPath == null) {
                changeStatus = "deleted";
                oldFilePath = null;
            } else {
                changeStatus = "modified";
                oldFilePath = effectiveOldPath.equals(filePath) ? null : effectiveOldPath;
            }
            return new PullRequestDiffFile(filePath, oldFilePath, changeStatus, lines);
        }
    }
}
