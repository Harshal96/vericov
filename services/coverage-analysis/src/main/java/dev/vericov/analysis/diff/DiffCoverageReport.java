package dev.vericov.analysis.diff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record DiffCoverageReport(
        String baseSha,
        String headSha,
        int patchLineCovered,
        int patchLineTotal,
        int newlyMissedLineCount,
        int lostCoverageLineCount,
        List<DiffCoverageFile> files) {

    public DiffCoverageReport {
        Objects.requireNonNull(baseSha, "baseSha");
        Objects.requireNonNull(headSha, "headSha");
        files = List.copyOf(files == null ? List.of() : files);
    }

    public BigDecimal patchLinePercentage() {
        if (patchLineTotal == 0) {
            return null;
        }
        return BigDecimal.valueOf(patchLineCovered)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(patchLineTotal), 2, RoundingMode.HALF_UP);
    }
}
