package dev.vericov.analysis.gates;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DebtItem(
        UUID id,
        String filePath,
        Integer startLine,
        Integer endLine,
        String status,
        Instant expiresAt) {

    public DebtItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(status, "status");
    }

    public boolean matches(Finding finding) {
        if (!matchPath(filePath, finding.filePath())) {
            return false;
        }
        if (startLine != null && endLine != null) {
            return finding.line() != null && finding.line() >= startLine && finding.line() <= endLine;
        }
        return true;
    }

    public static boolean matchPath(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (pattern.equals(path)) {
            return true;
        }
        // convert glob-like pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");
        try {
            return path.matches("^" + regex + "$");
        } catch (Exception e) {
            return false;
        }
    }
}
