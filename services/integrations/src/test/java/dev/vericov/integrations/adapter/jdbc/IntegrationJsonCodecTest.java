package dev.vericov.integrations.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationJsonCodecTest {
    @Test
    void decodesIntegralNumbersToSmallestPracticalJvmType() {
        IntegrationJsonCodec codec = new IntegrationJsonCodec();

        Map<String, Object> decoded = codec.fromJsonObject("""
                {
                  "int_value": 2147483647,
                  "long_value": 2147483648,
                  "decimal_value": 12.34,
                  "nested": {
                    "count": 5
                  },
                  "items": [1, 2147483648, 12.34]
                }
                """);

        assertEquals(2147483647, decoded.get("int_value"));
        assertInstanceOf(Integer.class, decoded.get("int_value"));
        assertEquals(2147483648L, decoded.get("long_value"));
        assertInstanceOf(Long.class, decoded.get("long_value"));
        assertEquals(new BigDecimal("12.34"), decoded.get("decimal_value"));
        assertEquals(Map.of("count", 5), decoded.get("nested"));
        assertEquals(List.of(1, 2147483648L, new BigDecimal("12.34")), decoded.get("items"));
    }
}
