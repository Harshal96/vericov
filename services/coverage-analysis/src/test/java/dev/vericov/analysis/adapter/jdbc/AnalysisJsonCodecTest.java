package dev.vericov.analysis.adapter.jdbc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisJsonCodecTest {

    @Test
    void roundTripsSupportedJsonObjectValues() {
        AnalysisJsonCodec codec = new AnalysisJsonCodec();
        Map<String, Object> values = Map.of(
                "scope", "project",
                "covered", 17,
                "total", 20L,
                "percentage", new BigDecimal("85.0000"),
                "blocking", true,
                "nested", Map.of("reason", "threshold"),
                "items", List.of("line", 1));

        Map<String, Object> decoded = codec.fromJsonObject(codec.toJsonObject(values));

        assertEquals("project", decoded.get("scope"));
        assertEquals(17, decoded.get("covered"));
        assertEquals(20, decoded.get("total"));
        assertEquals(new BigDecimal("85.0000"), decoded.get("percentage"));
        assertEquals(true, decoded.get("blocking"));
        assertEquals(Map.of("reason", "threshold"), decoded.get("nested"));
        assertEquals(List.of("line", 1), decoded.get("items"));
    }
}
