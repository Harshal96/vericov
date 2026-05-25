package dev.vericov.git.adapter.jdbc;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

public class GitJsonCodec {
    private final Jsonb jsonb = JsonbBuilder.create();

    public String toJson(Map<String, Object> value) {
        return jsonb.toJson(value == null ? Map.of() : value);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Object decoded = jsonb.fromJson(value, Map.class);
        if (!(decoded instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() != null) {
                copy.put(key, entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }
}
