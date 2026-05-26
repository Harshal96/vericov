package dev.vericov.analysis.gaps;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageRiskScorerTest {

    @Test
    void mapsRiskLevelBoundariesAndRoundsScores() {
        CoverageRiskScorer scorer = new CoverageRiskScorer();

        assertEquals(new BigDecimal("0.0"), scorer.normalizeScore(new BigDecimal("-4.0")));
        assertEquals(new BigDecimal("72.6"), scorer.normalizeScore(new BigDecimal("72.55")));
        assertEquals(new BigDecimal("100.0"), scorer.normalizeScore(new BigDecimal("122.0")));

        assertEquals("low", scorer.levelFor(new BigDecimal("34.9")));
        assertEquals("medium", scorer.levelFor(new BigDecimal("35.0")));
        assertEquals("medium", scorer.levelFor(new BigDecimal("64.9")));
        assertEquals("high", scorer.levelFor(new BigDecimal("65.0")));
        assertEquals("high", scorer.levelFor(new BigDecimal("84.9")));
        assertEquals("critical", scorer.levelFor(new BigDecimal("85.0")));
    }
}
