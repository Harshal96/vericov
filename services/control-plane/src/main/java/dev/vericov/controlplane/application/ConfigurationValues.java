package dev.vericov.controlplane.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConfigurationValues {
    private ConfigurationValues() {
    }

    static Map<String, Object> deepCopyMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, entryValue) -> copy.put(key, deepCopyValue(entryValue)));
        return Collections.unmodifiableMap(copy);
    }

    static List<Object> deepCopyList(List<?> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<Object> copy = new ArrayList<>();
        value.forEach(entry -> copy.add(deepCopyValue(entry)));
        return List.copyOf(copy);
    }

    private static Object deepCopyValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Double
                || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> copy = new LinkedHashMap<>();
            mapValue.forEach((key, entryValue) -> {
                if (!(key instanceof String stringKey)) {
                    throw new OrganizationException("validation_error", "config key must be a string");
                }
                copy.put(stringKey, deepCopyValue(entryValue));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> listValue) {
            return deepCopyList(listValue);
        }
        throw new OrganizationException("validation_error", "config value type is invalid");
    }
}
