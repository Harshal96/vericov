package dev.vericov.git.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class GitValues {
    private static final Pattern DETAIL_KEY_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");

    private GitValues() {
    }

    static UUID requireId(UUID value, String message) {
        if (value == null) {
            throw new GitIntegrationException("validation_error", message);
        }
        return value;
    }

    static String requireCanonical(String value, String message) {
        return requireTrimmed(value, message).toLowerCase(Locale.ROOT);
    }

    static String requireTrimmed(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new GitIntegrationException("validation_error", message);
        }
        return value.trim();
    }

    static String trimOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed;
    }

    static Map<String, Object> deepCopyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            copy.put(requireDetailKey(entry.getKey()), deepCopyValue(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static Object deepCopyValue(Object value) {
        if (value == null) {
            throw new GitIntegrationException("validation_error", "details value is invalid");
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Double doubleValue) {
            if (!Double.isFinite(doubleValue)) {
                throw new GitIntegrationException("validation_error", "details value is invalid");
            }
            return value;
        }
        if (value instanceof Float floatValue) {
            if (!Float.isFinite(floatValue)) {
                throw new GitIntegrationException("validation_error", "details value is invalid");
            }
            return value;
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger
                || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return deepCopyNestedMap(mapValue);
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(GitValues::deepCopyValue)
                    .toList();
        }
        throw new GitIntegrationException("validation_error", "details value is invalid");
    }

    private static Map<String, Object> deepCopyNestedMap(Map<?, ?> values) {
        if (values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            copy.put(requireDetailKey(entry.getKey()), deepCopyValue(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static String requireDetailKey(Object key) {
        if (!(key instanceof String stringKey)
                || stringKey.isBlank()
                || !DETAIL_KEY_PATTERN.matcher(stringKey).matches()) {
            throw new GitIntegrationException("validation_error", "details key is invalid");
        }
        return stringKey;
    }
}
