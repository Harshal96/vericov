package dev.vericov.ignore;

import java.util.Objects;
import java.util.regex.Pattern;

public final class CoveragePathPattern {
    private final String value;
    private final Pattern pattern;
    private final Specificity specificity;

    public CoveragePathPattern(String value) {
        CoverageIgnoreRules.CompiledPath compiled =
                CoverageIgnoreRules.compilePath(value, 0, false, "paths");
        this.value = Objects.requireNonNull(value, "value");
        this.pattern = compiled.pattern();
        this.specificity = compiled.specificity();
    }

    public String value() {
        return value;
    }

    public boolean matches(String path) {
        return pattern.matcher(CoverageIgnoreRules.normalizeRepositoryPath(path)).matches();
    }

    public Specificity specificity() {
        return specificity;
    }

    public record Specificity(int literalSegments, int literalCharacters)
            implements Comparable<Specificity> {
        @Override
        public int compareTo(Specificity other) {
            int segments = Integer.compare(literalSegments, other.literalSegments);
            return segments != 0
                    ? segments
                    : Integer.compare(literalCharacters, other.literalCharacters);
        }
    }
}
