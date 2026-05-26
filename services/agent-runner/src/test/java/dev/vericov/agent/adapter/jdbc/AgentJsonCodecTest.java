package dev.vericov.agent.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentJsonCodecTest {
    @Test
    void encodesAndDecodesNestedMetadataAsImmutableMaps() {
        AgentJsonCodec codec = new AgentJsonCodec();

        Map<String, Object> decoded = codec.fromJson(codec.toJson(Map.of(
                "agent", "coverage-gap-investigator",
                "details", Map.of("status", "approved"))));

        assertEquals("coverage-gap-investigator", decoded.get("agent"));
        assertEquals(Map.of("status", "approved"), decoded.get("details"));
        assertThrows(UnsupportedOperationException.class, () -> decoded.put("next", "value"));
    }

    @Test
    void treatsNullBlankAndNonObjectJsonAsEmptyMetadata() {
        AgentJsonCodec codec = new AgentJsonCodec();

        assertTrue(codec.fromJson(null).isEmpty());
        assertTrue(codec.fromJson(" ").isEmpty());
        assertTrue(codec.fromJson("[\"not\", \"an\", \"object\"]").isEmpty());
        assertEquals("{}", codec.toJson(null));
    }
}
