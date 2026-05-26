package dev.vericov.integrations.adapter.jdbc;

import dev.vericov.integrations.application.IntegrationException;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IntegrationJsonCodec {
    public String toJsonObject(Map<String, Object> values) {
        return jsonObjectBuilder(values).build().toString();
    }

    public Map<String, Object> fromJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try (var reader = Json.createReader(new StringReader(raw))) {
            return plainJsonObject(reader.readObject());
        }
    }

    public Map<String, Object> jsonObject(ResultSet resultSet, String columnName) throws SQLException {
        return fromJsonObject(resultSet.getString(columnName));
    }

    public Array textArray(Connection connection, List<String> values) throws SQLException {
        List<String> safeValues = values == null ? List.of() : values;
        return connection.createArrayOf("text", safeValues.toArray(String[]::new));
    }

    public List<String> textArray(ResultSet resultSet, String columnName) throws SQLException {
        Array sqlArray = resultSet.getArray(columnName);
        if (sqlArray == null) {
            return List.of();
        }
        Object values = sqlArray.getArray();
        if (values instanceof String[] stringValues) {
            return List.copyOf(Arrays.asList(stringValues));
        }
        if (values instanceof Object[] objectValues) {
            return Arrays.stream(objectValues)
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }

    private static JsonObjectBuilder jsonObjectBuilder(Map<?, ?> values) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (values == null) {
            return builder;
        }
        values.forEach((key, value) -> {
            if (!(key instanceof String stringKey)) {
                throw new IntegrationException("validation_error", "Integration config key must be a string");
            }
            builder.add(stringKey, jsonValue(value));
        });
        return builder;
    }

    private static JsonArrayBuilder jsonArrayBuilder(List<?> values) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        if (values != null) {
            values.forEach(value -> builder.add(jsonValue(value)));
        }
        return builder;
    }

    private static JsonValue jsonValue(Object value) {
        if (value == null) {
            return JsonValue.NULL;
        }
        if (value instanceof String stringValue) {
            return Json.createValue(stringValue);
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? JsonValue.TRUE : JsonValue.FALSE;
        }
        if (value instanceof Number numberValue) {
            return Json.createValue(new BigDecimal(numberValue.toString()));
        }
        if (value instanceof Map<?, ?> mapValue) {
            return jsonObjectBuilder(mapValue).build();
        }
        if (value instanceof List<?> listValue) {
            return jsonArrayBuilder(listValue).build();
        }
        throw new IntegrationException("validation_error", "Integration config value type is invalid");
    }

    private static Map<String, Object> plainJsonObject(JsonObject object) {
        Map<String, Object> values = new LinkedHashMap<>();
        object.forEach((key, value) -> values.put(key, plainJsonValue(value)));
        return Collections.unmodifiableMap(values);
    }

    private static Object plainJsonValue(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> plainJsonObject(value.asJsonObject());
            case ARRAY -> value.asJsonArray().stream()
                    .map(IntegrationJsonCodec::plainJsonValue)
                    .toList();
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> plainJsonNumber((JsonNumber) value);
            case TRUE -> true;
            case FALSE -> false;
            case NULL -> null;
        };
    }

    private static Object plainJsonNumber(JsonNumber number) {
        if (!number.isIntegral()) {
            return number.bigDecimalValue();
        }
        try {
            return number.intValueExact();
        } catch (ArithmeticException ignored) {
            // Try the next widest common in-memory representation.
        }
        try {
            return number.longValueExact();
        } catch (ArithmeticException ignored) {
            return number.bigDecimalValue();
        }
    }
}
