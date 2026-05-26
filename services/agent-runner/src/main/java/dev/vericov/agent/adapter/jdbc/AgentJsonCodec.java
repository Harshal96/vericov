package dev.vericov.agent.adapter.jdbc;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentJsonCodec {
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
        return copyMap(map);
    }

    private Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() != null) {
                copy.put(key, copyValue(entry.getValue()));
            }
        }
        return Map.copyOf(copy);
    }

    private Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        return value;
    }
}
