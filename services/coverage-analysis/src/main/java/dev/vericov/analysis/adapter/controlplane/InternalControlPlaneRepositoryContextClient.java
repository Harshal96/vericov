package dev.vericov.analysis.adapter.controlplane;

import dev.vericov.analysis.application.port.RepositoryContextRepository;
import dev.vericov.analysis.gates.RepositoryComponentContext;
import dev.vericov.analysis.gates.RepositoryContext;
import dev.vericov.analysis.gates.RepositoryOwnerRuleContext;
import dev.vericov.analysis.gates.RepositoryPackageNodeContext;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class InternalControlPlaneRepositoryContextClient implements RepositoryContextRepository {
    private final URI baseUri;
    private final String serviceToken;
    private final HttpClient httpClient;

    public InternalControlPlaneRepositoryContextClient(URI baseUri, String serviceToken) {
        this(baseUri, serviceToken, HttpClient.newHttpClient());
    }

    InternalControlPlaneRepositoryContextClient(URI baseUri, String serviceToken, HttpClient httpClient) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.serviceToken = Objects.requireNonNull(serviceToken, "serviceToken");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public RepositoryContext loadContext(
            UUID tenantId,
            UUID repositoryId,
            String commitSha,
            String branch,
            Integer pullRequestNumber) {
        URI uri = baseUri.resolve("/internal/v1/control-plane/repositories/" + repositoryId
                + "/coverage-context?commit_sha=" + encode(commitSha));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("X-Vericov-Service-Name", "coverage-analysis")
                .header("X-Vericov-Service-Token", serviceToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Coverage context request failed with HTTP " + response.statusCode());
            }
            return readContext(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Coverage context request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Coverage context request interrupted", exception);
        }
    }

    private static RepositoryContext readContext(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body == null ? "{}" : body))) {
            JsonObject envelope = reader.readObject();
            JsonObject data = envelope.getJsonObject("data");
            if (data == null) {
                throw new IllegalStateException("Coverage context response is missing data");
            }
            return new RepositoryContext(
                    data.getString("context_version"),
                    List.of(),
                    List.of(),
                    Map.of(),
                    readComponents(data.getJsonArray("components")),
                    readOwnerRules(data.getJsonArray("owner_rules")),
                    readPackageNodes(data.getJsonArray("package_nodes")));
        }
    }

    private static List<RepositoryComponentContext> readComponents(JsonArray components) {
        if (components == null) {
            return List.of();
        }
        List<RepositoryComponentContext> values = new ArrayList<>();
        for (JsonValue value : components) {
            JsonObject component = value.asJsonObject();
            values.add(new RepositoryComponentContext(
                    UUID.fromString(component.getString("id")),
                    component.getString("name"),
                    stringList(component.getJsonArray("path_patterns")),
                    stringList(component.getJsonArray("owners")),
                    component.getString("criticality", "medium"),
                    jsonMap(component.getJsonObject("metadata"))));
        }
        return List.copyOf(values);
    }

    private static List<RepositoryOwnerRuleContext> readOwnerRules(JsonArray ownerRules) {
        if (ownerRules == null) {
            return List.of();
        }
        List<RepositoryOwnerRuleContext> values = new ArrayList<>();
        for (JsonValue value : ownerRules) {
            JsonObject ownerRule = value.asJsonObject();
            values.add(new RepositoryOwnerRuleContext(
                    ownerRule.getString("source"),
                    ownerRule.getString("pattern"),
                    stringList(ownerRule.getJsonArray("owners")),
                    ownerRule.getInt("priority", 1000)));
        }
        return List.copyOf(values);
    }

    private static List<RepositoryPackageNodeContext> readPackageNodes(JsonArray packageNodes) {
        if (packageNodes == null) {
            return List.of();
        }
        List<RepositoryPackageNodeContext> values = new ArrayList<>();
        for (JsonValue value : packageNodes) {
            JsonObject packageNode = value.asJsonObject();
            values.add(new RepositoryPackageNodeContext(
                    nullableUuid(packageNode, "component_id"),
                    packageNode.getString("package_name"),
                    packageNode.getString("package_path"),
                    packageNode.getString("manifest_path"),
                    packageNode.getString("ecosystem", "unknown"),
                    jsonMap(packageNode.getJsonObject("metadata"))));
        }
        return List.copyOf(values);
    }

    private static List<String> stringList(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonValue value : array) {
            if (value instanceof JsonString string) {
                values.add(string.getString());
            }
        }
        return List.copyOf(values);
    }

    private static UUID nullableUuid(JsonObject object, String name) {
        JsonValue value = object.get(name);
        if (value instanceof JsonString string) {
            return UUID.fromString(string.getString());
        }
        return null;
    }

    private static Map<String, Object> jsonMap(JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        object.forEach((key, value) -> values.put(key, jsonValue(value)));
        return Map.copyOf(values);
    }

    private static Object jsonValue(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> jsonMap(value.asJsonObject());
            case ARRAY -> value.asJsonArray().stream().map(InternalControlPlaneRepositoryContextClient::jsonValue).toList();
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> ((JsonNumber) value).bigDecimalValue();
            case TRUE -> true;
            case FALSE -> false;
            case NULL -> null;
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
