package dev.vericov.git.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GitJsonCodecTest {
    @Test
    void encodesAndDecodesGitActionMetadataAsImmutableMaps() {
        GitJsonCodec codec = new GitJsonCodec();

        Map<String, Object> decoded = codec.fromJson(codec.toJson(Map.of(
                "provider_id", "check-run-123",
                "metadata", Map.of("conclusion", "success"))));

        assertEquals("check-run-123", decoded.get("provider_id"));
        assertEquals(Map.of("conclusion", "success"), decoded.get("metadata"));
        assertThrows(UnsupportedOperationException.class, () -> decoded.put("next", "value"));
    }

    @Test
    void treatsNullBlankAndNonObjectJsonAsEmptyMetadata() {
        GitJsonCodec codec = new GitJsonCodec();

        assertTrue(codec.fromJson(null).isEmpty());
        assertTrue(codec.fromJson(" ").isEmpty());
        assertTrue(codec.fromJson("[\"not\", \"an\", \"object\"]").isEmpty());
        assertEquals("{}", codec.toJson(null));
    }

    @Test
    void omitsNullValuesWhenReadingMetadata() {
        GitJsonCodec codec = new GitJsonCodec();

        assertEquals(Map.of("provider", "github"), codec.fromJson("{\"provider\":\"github\",\"ignored\":null}"));
    }
}
