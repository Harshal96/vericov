package dev.vericov.integrations.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class IntegrationConfigValues {
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");
    private static final List<String> SECRET_KEY_MARKERS = List.of(
            "secret",
            "password",
            "token",
            "apikey",
            "privatekey",
            "authorization",
            "credential",
            "clientsecret",
            "accesstoken",
            "refreshtoken");

    private IntegrationConfigValues() {
    }

    static String requireCanonical(String value, String fieldName) {
        return requireTrimmed(value, fieldName).toLowerCase(Locale.ROOT);
    }

    static String requireTrimmed(String value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName);
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    static Map<String, Object> deepCopyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String stringKey = requireConfigKey(entry.getKey());
            copy.put(stringKey, deepCopyValue(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static Object deepCopyValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("config value is invalid");
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Double doubleValue) {
            if (!Double.isFinite(doubleValue)) {
                throw new IllegalArgumentException("config value is invalid");
            }
            return value;
        }
        if (value instanceof Float floatValue) {
            if (!Float.isFinite(floatValue)) {
                throw new IllegalArgumentException("config value is invalid");
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
                    .map(IntegrationConfigValues::deepCopyValue)
                    .toList();
        }
        throw new IllegalArgumentException("config value is invalid");
    }

    private static Map<String, Object> deepCopyNestedMap(Map<?, ?> values) {
        if (values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String stringKey = requireConfigKey(entry.getKey());
            copy.put(stringKey, deepCopyValue(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static String requireConfigKey(Object key) {
        if (!(key instanceof String stringKey)) {
            throw new IllegalArgumentException("config keys must be strings");
        }
        if (stringKey.isBlank() || !CONFIG_KEY_PATTERN.matcher(stringKey).matches()) {
            throw new IllegalArgumentException("config key is invalid");
        }
        if (isSecretBearingKey(stringKey)) {
            throw new IllegalArgumentException("config contains secret-bearing key '" + stringKey + "'");
        }
        return stringKey;
    }

    private static boolean isSecretBearingKey(String key) {
        String compactKey = key.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(".", "");
        for (String marker : SECRET_KEY_MARKERS) {
            if (compactKey.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
