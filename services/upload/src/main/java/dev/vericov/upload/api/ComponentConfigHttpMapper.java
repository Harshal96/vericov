package dev.vericov.upload.api;

import dev.vericov.componentconfig.ComponentDefinition;
import dev.vericov.componentconfig.ComponentGates;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class ComponentConfigHttpMapper {
    private static final Set<String> ALLOWED =
            Set.of("key", "name", "owners", "gates", "paths", "components");

    private ComponentConfigHttpMapper() {
    }

    static List<ComponentDefinition> toDomain(List<Map<String, Object>> components) {
        if (components == null) {
            return List.of();
        }
        List<ComponentDefinition> result = new ArrayList<>();
        for (int index = 0; index < components.size(); index++) {
            result.add(toDomain(components.get(index), "components[" + index + "]"));
        }
        return List.copyOf(result);
    }

    private static ComponentDefinition toDomain(Map<String, Object> value, String path) {
        if (value == null) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        value.keySet().stream()
                .filter(key -> !ALLOWED.contains(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException(path + " contains unknown field " + key);
                });
        String key = string(value.get("key"), path + ".key", true);
        String name = string(value.get("name"), path + ".name", false);
        List<String> owners = value.containsKey("owners")
                ? strings(value.get("owners"), path + ".owners", true)
                : null;
        Map<String, BigDecimal> gates = numbers(value.get("gates"), path + ".gates");
        List<String> paths = strings(value.get("paths"), path + ".paths", false);
        List<ComponentDefinition> children = children(value.get("components"), path + ".components");
        return new ComponentDefinition(
                key,
                name,
                owners,
                new ComponentGates(gates),
                paths,
                children);
    }

    private static List<ComponentDefinition> children(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(path + " must be an array");
        }
        List<ComponentDefinition> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object child = values.get(index);
            if (!(child instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException(path + "[" + index + "] must be an object");
            }
            Map<String, Object> mapped = new java.util.LinkedHashMap<>();
            raw.forEach((key, item) -> mapped.put(String.valueOf(key), item));
            result.add(toDomain(mapped, path + "[" + index + "]"));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(Object value, String path, boolean nullable) {
        if (value == null) {
            return nullable ? null : List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(path + " must be an array");
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            result.add(string(values.get(index), path + "[" + index + "]", true));
        }
        return List.copyOf(result);
    }

    private static Map<String, BigDecimal> numbers(Object value, String path) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        Map<String, BigDecimal> result = new TreeMap<>();
        values.forEach((key, threshold) -> {
            if (!(threshold instanceof Number number)) {
                throw new IllegalArgumentException(path + "." + key + " must be numeric");
            }
            result.put(String.valueOf(key), new BigDecimal(number.toString()));
        });
        return result;
    }

    private static String string(Object value, String path, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(path + " is required");
            }
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException(path + " must be a string");
        }
        return string;
    }
}
