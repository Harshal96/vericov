package dev.vericov.componentconfig;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ComponentConfigJson {
    public static final int MAX_SNAPSHOT_BYTES = 256 * 1024;
    private static final Set<String> ROOT_KEYS = Set.of("version", "ignore", "components");
    private static final Set<String> COMPONENT_KEYS =
            Set.of("key", "name", "owners", "gates", "paths", "components");

    private ComponentConfigJson() {
    }

    public static ComponentConfigSnapshot parse(String json) {
        if (json == null || json.isBlank()) {
            throw new ComponentConfigException("Config snapshot JSON is required");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
            throw new ComponentConfigException("Config snapshot exceeds 256 KiB");
        }
        try (var reader = Json.createReader(new StringReader(json))) {
            JsonObject object = reader.readObject();
            rejectUnknown(object, ROOT_KEYS, "config");
            int version = object.getInt("version");
            List<String> ignore = strings(object.getJsonArray("ignore"), "ignore");
            List<ComponentDefinition> components = object.getJsonArray("components").stream()
                    .map(value -> parseComponent(value.asJsonObject()))
                    .toList();
            ComponentConfigSnapshot snapshot = new ComponentConfigSnapshot(version, ignore, components);
            ensureSize(snapshot);
            return snapshot;
        } catch (ComponentConfigException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ComponentConfigException("Invalid config snapshot JSON", exception);
        }
    }

    public static String canonicalJson(ComponentConfigSnapshot snapshot) {
        String canonical = toJson(snapshot).toString();
        if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
            throw new ComponentConfigException("Canonical config snapshot exceeds 256 KiB");
        }
        return canonical;
    }

    public static String sha256(ComponentConfigSnapshot snapshot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson(snapshot).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonObject toJson(ComponentConfigSnapshot snapshot) {
        JsonArrayBuilder components = Json.createArrayBuilder();
        snapshot.components().forEach(component -> components.add(toJson(component)));
        JsonArrayBuilder ignore = Json.createArrayBuilder();
        snapshot.ignore().forEach(ignore::add);
        return Json.createObjectBuilder()
                .add("components", components)
                .add("ignore", ignore)
                .add("version", snapshot.version())
                .build();
    }

    private static JsonObject toJson(ComponentDefinition component) {
        JsonArrayBuilder children = Json.createArrayBuilder();
        component.components().forEach(child -> children.add(toJson(child)));
        JsonObjectBuilder gates = Json.createObjectBuilder();
        new TreeMap<>(component.gates().thresholds()).forEach(gates::add);
        JsonArrayBuilder owners = Json.createArrayBuilder();
        if (component.owners() != null) {
            component.owners().forEach(owners::add);
        }
        JsonArrayBuilder paths = Json.createArrayBuilder();
        component.paths().forEach(paths::add);
        JsonObjectBuilder object = Json.createObjectBuilder()
                .add("components", children)
                .add("gates", gates)
                .add("key", component.key())
                .add("name", component.name());
        if (component.owners() == null) {
            object.addNull("owners");
        } else {
            object.add("owners", owners);
        }
        return object.add("paths", paths).build();
    }

    private static ComponentDefinition parseComponent(JsonObject object) {
        rejectUnknown(object, COMPONENT_KEYS, "component");
        String key = object.getString("key");
        String name = object.getString("name", key);
        List<String> owners = object.isNull("owners")
                ? null
                : strings(object.getJsonArray("owners"), "owners");
        Map<String, BigDecimal> gates = new TreeMap<>();
        JsonObject gateObject = object.getJsonObject("gates");
        gateObject.forEach((metric, value) -> gates.put(
                metric,
                gateObject.getJsonNumber(metric).bigDecimalValue()));
        List<String> paths = strings(object.getJsonArray("paths"), "paths");
        List<ComponentDefinition> children = object.getJsonArray("components").stream()
                .map(value -> parseComponent(value.asJsonObject()))
                .toList();
        return new ComponentDefinition(
                key,
                name,
                owners,
                new ComponentGates(gates),
                paths,
                children);
    }

    private static List<String> strings(JsonArray array, String path) {
        if (array == null) {
            throw new ComponentConfigException(path + " must be an array");
        }
        try {
            return array.getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString)
                    .toList();
        } catch (ClassCastException exception) {
            throw new ComponentConfigException(path + " must contain only strings", exception);
        }
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed, String path) {
        object.keySet().stream()
                .filter(key -> !allowed.contains(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new ComponentConfigException(path + " contains unknown field " + key);
                });
    }

    private static void ensureSize(ComponentConfigSnapshot snapshot) {
        canonicalJson(snapshot);
    }
}
