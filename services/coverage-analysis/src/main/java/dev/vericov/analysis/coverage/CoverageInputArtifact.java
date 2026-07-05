package dev.vericov.analysis.coverage;

import java.util.Locale;
import java.util.UUID;

public record CoverageInputArtifact(
        UUID artifactId,
        String name,
        String kind,
        String format,
        String storageBucket,
        String storagePath,
        String sha256Hex) {

    public CoverageInputArtifact(
            String name,
            String kind,
            String format,
            String storageBucket,
            String storagePath,
            String sha256Hex) {
        this(null, name, kind, format, storageBucket, storagePath, sha256Hex);
    }

    public boolean isCoverageArtifact() {
        return "coverage".equals(kind);
    }

    public boolean isTestResultArtifact() {
        return "test_results".equals(kind);
    }

    public boolean isDiffArtifact() {
        return "diff".equals(kind);
    }

    public String normalizedFormat() {
        return format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizedName() {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLcov() {
        String formatName = normalizedFormat();
        String artifactName = normalizedName();
        return "lcov".equals(formatName) || artifactName.endsWith(".lcov") || artifactName.endsWith(".info");
    }
}
