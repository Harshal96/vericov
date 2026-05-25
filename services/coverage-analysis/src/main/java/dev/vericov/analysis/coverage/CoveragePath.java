package dev.vericov.analysis.coverage;

public final class CoveragePath {
    private CoveragePath() {
    }

    public static String normalize(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }
}
