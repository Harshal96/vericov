package dev.vericov.analysis.adapter.jdbc;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalysisJsonCodec {
    public String toJsonObject(Map<String, Object> values) {
        return jsonObjectBuilder(values).build().toString();
    }

    public String toJsonArray(List<?> values) {
        return jsonArrayBuilder(values).build().toString();
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

    private static JsonObjectBuilder jsonObjectBuilder(Map<?, ?> values) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (values == null) {
            return builder;
        }
        values.forEach((key, value) -> {
            if (!(key instanceof String stringKey)) {
                throw new IllegalArgumentException("JSON object key must be a string");
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
        throw new IllegalArgumentException("JSON object value type is invalid");
    }

    private static Map<String, Object> plainJsonObject(JsonObject object) {
        Map<String, Object> values = new LinkedHashMap<>();
        object.forEach((key, value) -> values.put(key, plainJsonValue(value)));
        return Map.copyOf(values);
    }

    private static Object plainJsonValue(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> plainJsonObject(value.asJsonObject());
            case ARRAY -> value.asJsonArray().stream()
                    .map(AnalysisJsonCodec::plainJsonValue)
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
