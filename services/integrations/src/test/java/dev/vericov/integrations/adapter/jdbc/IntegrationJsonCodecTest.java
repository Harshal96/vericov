package dev.vericov.integrations.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.integrations.application.IntegrationException;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IntegrationJsonCodecTest {
    @Test
    void decodesIntegralNumbersToSmallestPracticalJvmType() {
        IntegrationJsonCodec codec = new IntegrationJsonCodec();

        Map<String, Object> decoded = codec.fromJsonObject("""
                {
                  "int_value": 2147483647,
                  "long_value": 2147483648,
                  "decimal_value": 12.34,
                  "nested": {
                    "count": 5
                  },
                  "items": [1, 2147483648, 12.34]
                }
                """);

        assertEquals(2147483647, decoded.get("int_value"));
        assertInstanceOf(Integer.class, decoded.get("int_value"));
        assertEquals(2147483648L, decoded.get("long_value"));
        assertInstanceOf(Long.class, decoded.get("long_value"));
        assertEquals(new BigDecimal("12.34"), decoded.get("decimal_value"));
        assertEquals(Map.of("count", 5), decoded.get("nested"));
        assertEquals(List.of(1, 2147483648L, new BigDecimal("12.34")), decoded.get("items"));
    }

    @Test
    void encodesObjectsListsBooleansAndNullsAsJsonObjects() {
        IntegrationJsonCodec codec = new IntegrationJsonCodec();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("provider", "github");
        values.put("enabled", true);
        values.put("limits", Map.of("max", 5));
        values.put("capabilities", List.of("git.checks", "git.comments"));
        values.put("nullable", null);

        Map<String, Object> decoded = codec.fromJsonObject(codec.toJsonObject(values));

        assertEquals("github", decoded.get("provider"));
        assertEquals(true, decoded.get("enabled"));
        assertEquals(Map.of("max", 5), decoded.get("limits"));
        assertEquals(List.of("git.checks", "git.comments"), decoded.get("capabilities"));
        assertTrue(decoded.containsKey("nullable"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsNonStringKeysAndUnsupportedValueTypes() {
        IntegrationJsonCodec codec = new IntegrationJsonCodec();
        Map invalidKey = Map.of(42, "invalid");

        IntegrationException keyException = assertThrows(
                IntegrationException.class,
                () -> codec.toJsonObject(invalidKey));
        IntegrationException valueException = assertThrows(
                IntegrationException.class,
                () -> codec.toJsonObject(Map.of("invalid", new Object())));

        assertEquals("validation_error", keyException.code());
        assertEquals("validation_error", valueException.code());
    }

    @Test
    void convertsJdbcTextArraysToImmutableLists() throws SQLException {
        IntegrationJsonCodec codec = new IntegrationJsonCodec();

        assertEquals(
                List.of("git.checks", "git.comments"),
                codec.textArray(resultSet(array(new String[] {"git.checks", "git.comments"})), "capabilities"));
        assertEquals(List.of("1", "2"), codec.textArray(resultSet(array(new Object[] {1, 2})), "capabilities"));
        assertTrue(codec.textArray(resultSet(null), "capabilities").isEmpty());
    }

    @Test
    void createsJdbcTextArraysWithSafeDefaultValues() throws SQLException {
        IntegrationJsonCodec codec = new IntegrationJsonCodec();
        AtomicReference<String> typeName = new AtomicReference<>();
        AtomicReference<Object[]> elements = new AtomicReference<>();
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if ("createArrayOf".equals(method.getName())) {
                        typeName.set((String) args[0]);
                        elements.set((Object[]) args[1]);
                        return array(elements.get());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        Array array = codec.textArray(connection, List.of("git.checks", "git.comments"));

        assertEquals("text", typeName.get());
        assertEquals(List.of("git.checks", "git.comments"), List.of((Object[]) array.getArray()));
        Array emptyArray = codec.textArray(connection, null);
        assertTrue(List.of((Object[]) emptyArray.getArray()).isEmpty());
    }

    private static ResultSet resultSet(Array array) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (proxy, method, args) -> {
                    if ("getArray".equals(method.getName())) {
                        return array;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Array array(Object values) {
        return (Array) Proxy.newProxyInstance(
                Array.class.getClassLoader(),
                new Class<?>[] {Array.class},
                (proxy, method, args) -> {
                    if ("getArray".equals(method.getName())) {
                        return values;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
