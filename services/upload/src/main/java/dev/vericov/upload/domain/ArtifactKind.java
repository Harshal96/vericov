package dev.vericov.upload.domain;

import java.util.Locale;

public enum ArtifactKind {
    COVERAGE("coverage"),
    TEST_RESULTS("test_results"),
    METADATA("metadata");

    private final String wireValue;

    ArtifactKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ArtifactKind fromWireValue(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ArtifactKind kind : values()) {
            if (kind.wireValue.equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unsupported artifact kind: " + value);
    }
}
