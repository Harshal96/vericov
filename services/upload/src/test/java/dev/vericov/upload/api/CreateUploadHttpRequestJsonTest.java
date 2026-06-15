package dev.vericov.upload.api;

import jakarta.json.bind.JsonbBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreateUploadHttpRequestJsonTest {
    @Test
    void deserializesIgnoreRulesFromUploadJson() {
        String json = """
                {
                  "commit_sha": "smoke-test",
                  "branch": "main",
                  "ci_provider": "local",
                  "ignore": ["generated/**"],
                  "components": [{
                    "components": [],
                    "gates": {"line": 90},
                    "key": "api",
                    "name": "API",
                    "owners": ["team-api"],
                    "paths": ["services/api/**"]
                  }],
                  "config_sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "artifacts": [{
                    "name": "coverage.lcov",
                    "kind": "coverage",
                    "format": "lcov",
                    "content_type": "text/plain",
                    "content_base64": "VE46Cg=="
                  }]
                }
                """;

        try (var jsonb = JsonbBuilder.create()) {
            CreateUploadHttpRequest request = jsonb.fromJson(json, CreateUploadHttpRequest.class);

            assertNull(request.repositoryId());
            assertEquals("smoke-test", request.commitSha());
            assertEquals(List.of("generated/**"), request.ignore());
            assertEquals("api", request.components().getFirst().get("key"));
            assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", request.configSha256());
            assertEquals(List.of(), request.flags());
            assertEquals(1, request.artifacts().size());
        } catch (Exception exception) {
            throw new AssertionError("Upload JSON should deserialize", exception);
        }
    }
}
