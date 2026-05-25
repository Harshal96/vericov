package dev.vericov.analysis.coverage;

import java.util.Locale;

public record CoverageInputArtifact(
        String name,
        String kind,
        String format,
        String storageBucket,
        String storagePath,
        String sha256Hex) {

    public boolean isCoverageArtifact() {
        return "coverage".equals(kind);
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
