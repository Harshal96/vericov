package dev.vericov.componentconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ComponentConfigContractTest {
    @Test
    void matchesCanonicalSnapshotFixtures() throws IOException {
        try (var reader = Json.createReader(Files.newBufferedReader(
                contractRoot().resolve("component-config-snapshots.json")))) {
            var cases = reader.readObject().getJsonArray("cases");
            for (var value : cases) {
                var testCase = value.asJsonObject();
                ComponentConfigSnapshot snapshot =
                        ComponentConfigJson.parse(testCase.getJsonObject("snapshot").toString());
                assertEquals(
                        testCase.getString("canonical"),
                        ComponentConfigJson.canonicalJson(snapshot),
                        testCase.getString("name"));
                assertEquals(
                        testCase.getString("sha256"),
                        ComponentConfigJson.sha256(snapshot),
                        testCase.getString("name"));
            }
        }
    }

    private static Path contractRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("test-contracts");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("test-contracts directory not found");
    }
}
