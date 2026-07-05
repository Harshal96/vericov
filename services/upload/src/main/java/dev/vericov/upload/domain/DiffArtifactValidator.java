package dev.vericov.upload.domain;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Validates a {@code diff} upload artifact before persistence: size, that it
 * parses as a unified diff, and that every path inside it is safe. Limits
 * must match {@code UnifiedDiffParser} in the coverage-analysis service.
 */
public final class DiffArtifactValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_FILES = 10_000;
    public static final int MAX_CHANGED_LINES = 500_000;

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)? @@.*$");

    private DiffArtifactValidator() {
    }

    public static void validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidDiffArtifactException("diff artifact content must not be empty");
        }
        if (content.length > MAX_BYTES) {
            throw new InvalidDiffArtifactException("diff exceeds the " + MAX_BYTES + " byte limit");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        String[] lines = text.split("\n", -1);
        int fileCount = 0;
        int changedLines = 0;
        boolean inHunk = false;
        boolean sawFileHeader = false;
        for (String line : lines) {
            if (line.startsWith("diff --git ")) {
                fileCount++;
                inHunk = false;
                sawFileHeader = true;
                if (fileCount > MAX_FILES) {
                    throw new InvalidDiffArtifactException("diff touches more than " + MAX_FILES + " files");
                }
            } else if (line.startsWith("--- ") || line.startsWith("+++ ")) {
                validatePath(line.substring(4));
            } else if (line.startsWith("rename from ") || line.startsWith("rename to ")) {
                int prefixLength = line.startsWith("rename from ") ? "rename from ".length() : "rename to ".length();
                validatePath(line.substring(prefixLength));
            } else if (HUNK_HEADER.matcher(line).matches()) {
                inHunk = true;
            } else if (inHunk && !line.isEmpty()
                    && (line.charAt(0) == ' ' || line.charAt(0) == '+' || line.charAt(0) == '-')
                    && !line.startsWith("+++") && !line.startsWith("---")) {
                changedLines++;
                if (changedLines > MAX_CHANGED_LINES) {
                    throw new InvalidDiffArtifactException("diff exceeds " + MAX_CHANGED_LINES + " changed lines");
                }
            }
        }
        if (!sawFileHeader) {
            throw new InvalidDiffArtifactException("diff content does not parse as a unified diff");
        }
    }

    private static void validatePath(String rawPath) {
        String trimmed = rawPath.trim();
        int tabIndex = trimmed.indexOf('\t');
        if (tabIndex >= 0) {
            trimmed = trimmed.substring(0, tabIndex);
        }
        if ("/dev/null".equals(trimmed)) {
            return;
        }
        String stripped = trimmed.startsWith("a/") || trimmed.startsWith("b/") ? trimmed.substring(2) : trimmed;
        String normalized = stripped.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.contains("/../")
                || normalized.endsWith("/..")) {
            throw new InvalidDiffArtifactException("diff contains an invalid file path: " + rawPath);
        }
    }

    public static final class InvalidDiffArtifactException extends RuntimeException {
        public InvalidDiffArtifactException(String message) {
            super(message);
        }
    }
}
