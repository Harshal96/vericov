package dev.vericov.git.adapter.provider.github;

import dev.vericov.git.application.GitAnnotationInput;
import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;
import dev.vericov.git.application.GitProviderActionType;
import dev.vericov.git.application.port.CredentialLease;
import dev.vericov.git.application.port.GitProviderPullRequestDiffQuery;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubProviderClientTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void createCheckRunPostsGitHubCheckRunWithAnnotations() {
        RecordingGitHubTransport transport = new RecordingGitHubTransport("""
                {"id":987654,"status":"completed","html_url":"https://github.com/acme/widget/runs/987654"}
                """);
        GitHubProviderClient client = new GitHubProviderClient(
                URI.create("https://api.github.test"),
                action -> "installation-token",
                transport);

        GitProviderActionResult result = client.execute(action(
                GitProviderActionType.CREATE_OR_UPDATE_CHECK_RUN,
                Map.of(
                        "check_name", "Vericov Coverage",
                        "commit_sha", "abc123",
                        "status", "completed",
                        "conclusion", "failure",
                        "summary", "Patch coverage failed",
                        "details_url", "https://app.vericov.test/reports/1",
                        "annotations", List.of(Map.of(
                                "path", "src/App.java",
                                "start_line", 12,
                                "end_line", 12,
                                "annotation_level", "warning",
                                "message", "Changed line is uncovered")))));

        assertEquals("987654", result.providerId());
        assertEquals("completed", result.status());
        assertEquals("https://github.com/acme/widget/runs/987654", result.providerUrl());
        assertEquals("POST", transport.lastRequest.method());
        assertEquals("/repos/acme/widget/check-runs", transport.lastRequest.uri().getPath());
        assertEquals("Bearer installation-token", transport.lastRequest.headers().firstValue("Authorization").orElseThrow());
        assertTrue(transport.lastBody.contains("\"name\":\"Vericov Coverage\""));
        assertTrue(transport.lastBody.contains("\"head_sha\":\"abc123\""));
        assertTrue(transport.lastBody.contains("\"annotations\""));
        assertTrue(transport.lastBody.contains("\"details_url\":\"https://app.vericov.test/reports/1\""));
    }

    @Test
    void createBranchTreatsExistingRefAsIdempotentSuccess() {
        RecordingGitHubTransport transport = new RecordingGitHubTransport(422, """
                {"message":"Reference already exists"}
                """);
        GitHubProviderClient client = new GitHubProviderClient(
                URI.create("https://api.github.test"),
                action -> "installation-token",
                transport);

        GitProviderActionResult result = client.execute(action(
                GitProviderActionType.CREATE_BRANCH,
                Map.of(
                        "branch_name", "vericov/add-tests",
                        "base_sha", "abc123",
                        "idempotency_key", "branch-key")));

        assertEquals("already_exists", result.status());
        assertEquals("refs/heads/vericov/add-tests", result.providerId());
        assertEquals("/repos/acme/widget/git/refs", transport.lastRequest.uri().getPath());
    }

    @Test
    void fetchPullRequestDiffUsesExactCompareAndParsesFilePatches() {
        RecordingGitHubTransport transport = new RecordingGitHubTransport(200, """
                {
                  "files": [
                    {
                      "filename": "src/App.java",
                      "status": "modified",
                      "patch": "@@ -1,1 +1,2 @@\\n unchanged\\n+added"
                    }
                  ]
                }
                """);
        GitHubProviderClient client = new GitHubProviderClient(
                URI.create("https://api.github.test"),
                action -> "installation-token",
                transport);

        var diff = client.fetchPullRequestDiff(query("base123", "head456"));

        assertEquals("base123", diff.baseSha());
        assertEquals("head456", diff.headSha());
        assertEquals("src/App.java", diff.files().getFirst().filePath());
        assertEquals(2, diff.files().getFirst().lines().size());
        assertEquals("/repos/acme/widget/compare/base123...head456", transport.lastRequest.uri().getPath());
    }

    private static GitProviderAction action(GitProviderActionType type, Map<String, Object> details) {
        return new GitProviderAction(
                type,
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                CONNECTION_ID,
                "github",
                "acme/widget",
                "git.checks",
                "github_installation_token",
                new CredentialLease(UUID.randomUUID(), "github_installation_token", "token".toCharArray(), Instant.now().plusSeconds(60)),
                Map.of("installation_id", "123456"),
                Map.of(),
                details);
    }

    private static GitProviderPullRequestDiffQuery query(String baseSha, String headSha) {
        return new GitProviderPullRequestDiffQuery(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                CONNECTION_ID,
                "github",
                "acme/widget",
                "git.repository_sync",
                "github_installation_token",
                new CredentialLease(UUID.randomUUID(), "github_installation_token", "token".toCharArray(), Instant.now().plusSeconds(60)),
                Map.of("installation_id", "123456"),
                Map.of(),
                42,
                baseSha,
                headSha);
    }

    private static final class RecordingGitHubTransport implements GitHubProviderClient.HttpTransport {
        private final int statusCode;
        private final String responseBody;
        private HttpRequest lastRequest;
        private String lastBody;

        private RecordingGitHubTransport(String responseBody) {
            this(201, responseBody);
        }

        private RecordingGitHubTransport(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public GitHubProviderClient.HttpResult send(HttpRequest request, String body) {
            lastRequest = request;
            lastBody = body;
            return new GitHubProviderClient.HttpResult(statusCode, responseBody);
        }
    }
}
