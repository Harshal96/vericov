package dev.vericov.analysis.coverage;

public record CoverageMetric(int covered, int total) {

    public CoverageMetric {
        if (covered < 0) {
            throw new IllegalArgumentException("covered must be non-negative");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
        if (covered > total) {
            throw new IllegalArgumentException("covered must be less than or equal to total");
        }
    }

    public double percentage() {
        return total == 0 ? 100.0 : (covered * 100.0) / total;
    }
}
