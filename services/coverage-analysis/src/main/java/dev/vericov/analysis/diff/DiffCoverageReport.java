package dev.vericov.analysis.diff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record DiffCoverageReport(
        String baseSha,
        String headSha,
        String status,
        int patchLineCovered,
        int patchLineTotal,
        int newlyMissedLineCount,
        int lostCoverageLineCount,
        List<DiffCoverageFile> files) {

    public DiffCoverageReport {
        Objects.requireNonNull(baseSha, "baseSha");
        Objects.requireNonNull(headSha, "headSha");
        status = status == null || status.isBlank() ? "complete" : status;
        files = List.copyOf(files == null ? List.of() : files);
    }

    public DiffCoverageReport(
            String baseSha,
            String headSha,
            int patchLineCovered,
            int patchLineTotal,
            int newlyMissedLineCount,
            int lostCoverageLineCount,
            List<DiffCoverageFile> files) {
        this(
                baseSha,
                headSha,
                "complete",
                patchLineCovered,
                patchLineTotal,
                newlyMissedLineCount,
                lostCoverageLineCount,
                files);
    }

    public DiffCoverageReport withStatus(String nextStatus) {
        return new DiffCoverageReport(
                baseSha,
                headSha,
                nextStatus,
                patchLineCovered,
                patchLineTotal,
                newlyMissedLineCount,
                lostCoverageLineCount,
                files);
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
