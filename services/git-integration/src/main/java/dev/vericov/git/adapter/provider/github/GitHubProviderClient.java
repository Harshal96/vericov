package dev.vericov.git.adapter.provider.github;

import dev.vericov.git.application.GitDiffFileDetails;
import dev.vericov.git.application.GitDiffLineDetails;
import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitPullRequestDiffDetails;
import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;
import dev.vericov.git.application.GitProviderActionType;
import dev.vericov.git.application.port.GitProviderPullRequestDiffQuery;
import dev.vericov.git.application.port.GitProviderQueryPort;
import dev.vericov.git.application.port.GitProviderClient;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class GitHubProviderClient implements GitProviderClient, GitProviderQueryPort {
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION = "2022-11-28";

    private final URI baseUri;
    private final AccessTokenProvider accessTokenProvider;
    private final HttpTransport transport;
    private final Jsonb jsonb = JsonbBuilder.create();

    public GitHubProviderClient(URI baseUri, AccessTokenProvider accessTokenProvider) {
        this(baseUri, accessTokenProvider, new JavaHttpTransport(HttpClient.newHttpClient()));
    }

    public GitHubProviderClient(URI baseUri, AccessTokenProvider accessTokenProvider, HttpTransport transport) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public GitProviderActionResult execute(GitProviderAction action) {
        Objects.requireNonNull(action, "action");
        return switch (action.type()) {
            case CREATE_OR_UPDATE_CHECK_RUN -> createCheckRun(action);
            case CREATE_OR_UPDATE_PR_COMMENT -> createOrUpdatePrComment(action);
            case CREATE_OR_UPDATE_PR_ANNOTATIONS -> createOrUpdatePrAnnotations(action);
            case CREATE_BRANCH -> createBranch(action);
            case OPEN_PULL_REQUEST -> openPullRequest(action);
        };
    }

    @Override
    public GitPullRequestDiffDetails fetchPullRequestDiff(GitProviderPullRequestDiffQuery query) {
        Objects.requireNonNull(query, "query");
        GitProviderAction tokenAction = tokenAction(query);
        String baseHead = encodePathSegment(query.baseSha() + "..." + query.headSha());
        HttpResult result = sendRaw(
                tokenAction,
                "GET",
                "/repos/" + repoPath(tokenAction) + "/compare/" + baseHead,
                null,
                200);
        JsonObject json = readObject(result.body());
        JsonArray files = json.getJsonArray("files");
        if (files == null) {
            return new GitPullRequestDiffDetails(
                    query.repositoryId(),
                    query.pullRequestNumber(),
                    query.baseSha(),
                    query.headSha(),
                    List.of());
        }
        if (files.size() >= 300) {
            throw new GitIntegrationException(
                    "validation_error",
                    "Pull request diff is too large for exact coverage analysis");
        }
        GitHubUnifiedDiffParser parser = new GitHubUnifiedDiffParser();
        List<GitDiffFileDetails> diffFiles = files.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(file -> diffFile(file, parser))
                .toList();
        return new GitPullRequestDiffDetails(
                query.repositoryId(),
                query.pullRequestNumber(),
                query.baseSha(),
                query.headSha(),
                diffFiles);
    }

    private GitProviderActionResult createCheckRun(GitProviderAction action) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("title", requiredString(action.details(), "check_name"));
        output.put("summary", optionalString(action.details(), "summary", ""));
        String text = optionalString(action.details(), "text", null);
        if (text != null) {
            output.put("text", text);
        }
        Object annotations = action.details().get("annotations");
        if (annotations instanceof List<?> list && !list.isEmpty()) {
            output.put("annotations", list);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", requiredString(action.details(), "check_name"));
        body.put("head_sha", requiredString(action.details(), "commit_sha"));
        body.put("status", requiredString(action.details(), "status"));
        putOptional(body, "conclusion", optionalString(action.details(), "conclusion", null));
        putOptional(body, "details_url", optionalString(action.details(), "details_url", null));
        body.put("output", output);

        HttpResult result = send(action, "POST", "/repos/" + repoPath(action) + "/check-runs", body);
        JsonObject json = readObject(result.body());
        return new GitProviderActionResult(
                action.type(),
                fieldAsString(json, "id"),
                fieldAsString(json, "status", requiredString(action.details(), "status")),
                fieldAsString(json, "html_url"),
                Map.of());
    }

    private GitProviderActionResult createOrUpdatePrComment(GitProviderAction action) {
        int pullRequestNumber = requiredInt(action.details(), "pull_request_number");
        String marker = "<!-- vericov:" + requiredString(action.details(), "marker") + " -->";
        String body = marker + "\n" + requiredString(action.details(), "body");
        JsonObject existing = findExistingIssueComment(action, pullRequestNumber, marker);
        HttpResult result;
        if (existing == null) {
            result = send(
                    action,
                    "POST",
                    "/repos/" + repoPath(action) + "/issues/" + pullRequestNumber + "/comments",
                    Map.of("body", body));
        } else {
            result = send(
                    action,
                    "PATCH",
                    "/repos/" + repoPath(action) + "/issues/comments/" + fieldAsString(existing, "id"),
                    Map.of("body", body));
        }
        JsonObject json = readObject(result.body());
        return new GitProviderActionResult(
                action.type(),
                fieldAsString(json, "id"),
                existing == null ? "posted" : "updated",
                fieldAsString(json, "html_url"),
                Map.of());
    }

    private GitProviderActionResult createOrUpdatePrAnnotations(GitProviderAction action) {
        int pullRequestNumber = requiredInt(action.details(), "pull_request_number");
        String marker = "<!-- vericov:annotations:" + requiredString(action.details(), "annotation_batch_key") + " -->";
        String body = marker + "\n" + annotationSummary(action.details().get("annotations"));
        JsonObject existing = findExistingIssueComment(action, pullRequestNumber, marker);
        HttpResult result;
        if (existing == null) {
            result = send(
                    action,
                    "POST",
                    "/repos/" + repoPath(action) + "/issues/" + pullRequestNumber + "/comments",
                    Map.of("body", body));
        } else {
            result = send(
                    action,
                    "PATCH",
                    "/repos/" + repoPath(action) + "/issues/comments/" + fieldAsString(existing, "id"),
                    Map.of("body", body));
        }
        JsonObject json = readObject(result.body());
        return new GitProviderActionResult(
                action.type(),
                fieldAsString(json, "id"),
                existing == null ? "posted" : "updated",
                fieldAsString(json, "html_url"),
                Map.of());
    }

    private GitProviderActionResult createBranch(GitProviderAction action) {
        String branchName = requiredString(action.details(), "branch_name");
        String ref = "refs/heads/" + branchName;
        HttpResult result = sendAllowingStatus(
                action,
                "POST",
                "/repos/" + repoPath(action) + "/git/refs",
                Map.of("ref", ref, "sha", requiredString(action.details(), "base_sha")),
                201,
                422);
        if (result.statusCode() == 422 && result.body().toLowerCase(Locale.ROOT).contains("reference already exists")) {
            return new GitProviderActionResult(action.type(), ref, "already_exists", null, Map.of());
        }
        JsonObject json = readObject(result.body());
        return new GitProviderActionResult(
                action.type(),
                fieldAsString(json, "ref", ref),
                "created",
                fieldAsString(json, "url"),
                Map.of());
    }

    private GitProviderActionResult openPullRequest(GitProviderAction action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("head", requiredString(action.details(), "source_branch"));
        body.put("base", requiredString(action.details(), "target_branch"));
        body.put("title", requiredString(action.details(), "title"));
        body.put("body", requiredString(action.details(), "body"));
        body.put("draft", requiredBoolean(action.details(), "draft"));

        HttpResult result = send(action, "POST", "/repos/" + repoPath(action) + "/pulls", body);
        JsonObject json = readObject(result.body());
        return new GitProviderActionResult(
                action.type(),
                fieldAsString(json, "id"),
                "created",
                fieldAsString(json, "html_url"),
                Map.of("number", fieldAsString(json, "number", "")));
    }

    private JsonObject findExistingIssueComment(GitProviderAction action, int issueNumber, String marker) {
        HttpResult result = sendRaw(
                action,
                "GET",
                "/repos/" + repoPath(action) + "/issues/" + issueNumber + "/comments?per_page=100",
                null,
                200);
        JsonArray comments = readArray(result.body());
        for (JsonValue commentValue : comments) {
            if (commentValue instanceof JsonObject comment
                    && comment.getString("body", "").contains(marker)) {
                return comment;
            }
        }
        return null;
    }

    private HttpResult send(GitProviderAction action, String method, String path, Map<String, Object> body) {
        return sendRaw(action, method, path, jsonb.toJson(body), expectedSuccess(method));
    }

    private HttpResult sendAllowingStatus(
            GitProviderAction action,
            String method,
            String path,
            Map<String, Object> body,
            int firstExpectedStatus,
            int secondExpectedStatus) {
        return sendRaw(action, method, path, jsonb.toJson(body), firstExpectedStatus, secondExpectedStatus);
    }

    private HttpResult sendRaw(
            GitProviderAction action,
            String method,
            String path,
            String body,
            int... expectedStatuses) {
        String token = requiredToken(accessTokenProvider.accessToken(action));
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Accept", ACCEPT)
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", API_VERSION);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        try {
            HttpResult result = transport.send(builder.build(), body);
            for (int expectedStatus : expectedStatuses) {
                if (result.statusCode() == expectedStatus) {
                    return result;
                }
            }
            throw new GitIntegrationException("provider_error", "GitHub request failed with HTTP " + result.statusCode());
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub request interrupted", exception);
        }
    }

    private static int expectedSuccess(String method) {
        return "PATCH".equals(method) ? 200 : 201;
    }

    private static String repoPath(GitProviderAction action) {
        String[] parts = required(action.externalRepositoryId(), "external_repository_id").split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new GitIntegrationException("validation_error", "GitHub external repository id must use owner/repo");
        }
        return encodePathSegment(parts[0]) + "/" + encodePathSegment(parts[1]);
    }

    private static GitDiffFileDetails diffFile(JsonObject file, GitHubUnifiedDiffParser parser) {
        String filePath = fieldAsString(file, "filename");
        String oldFilePath = fieldAsString(file, "previous_filename");
        String status = fieldAsString(file, "status", "modified");
        String patch = fieldAsString(file, "patch", "");
        List<GitDiffLineDetails> lines = parser.parse(patch);
        return new GitDiffFileDetails(filePath, oldFilePath, status, lines);
    }

    private static GitProviderAction tokenAction(GitProviderPullRequestDiffQuery query) {
        return new GitProviderAction(
                GitProviderActionType.CREATE_OR_UPDATE_PR_ANNOTATIONS,
                query.tenantId(),
                query.orgId(),
                query.repositoryId(),
                query.connectionId(),
                query.providerKey(),
                query.externalRepositoryId(),
                query.requiredCapability(),
                query.credentialKind(),
                query.credentialLease(),
                query.connectionConfig(),
                query.bindingConfig(),
                Map.of("pull_request_number", query.pullRequestNumber(), "annotation_batch_key", "diff-read"));
    }

    private static String annotationSummary(Object annotations) {
        if (!(annotations instanceof List<?> values) || values.isEmpty()) {
            return "No annotations.";
        }
        StringBuilder builder = new StringBuilder("Vericov annotations:\n");
        for (Object value : values) {
            if (value instanceof Map<?, ?> annotation) {
                builder.append("- ")
                        .append(valueOrFallback(annotation, "path", "unknown"))
                        .append(":")
                        .append(valueOrFallback(annotation, "start_line", "?"))
                        .append(" ")
                        .append(valueOrFallback(annotation, "message", ""))
                        .append("\n");
            }
        }
        return builder.toString().trim();
    }

    private static Object valueOrFallback(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value;
    }

    private static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof String string && !string.trim().isBlank()) {
            return string.trim();
        }
        throw new GitIntegrationException("validation_error", key + " is required");
    }

    private static String optionalString(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        if (value instanceof String string && !string.trim().isBlank()) {
            return string.trim();
        }
        return fallback;
    }

    private static int requiredInt(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        throw new GitIntegrationException("validation_error", key + " must be positive");
    }

    private static boolean requiredBoolean(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new GitIntegrationException("validation_error", key + " is required");
    }

    private static void putOptional(Map<String, Object> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private static JsonObject readObject(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body == null ? "{}" : body))) {
            JsonValue value = reader.readValue();
            if (value instanceof JsonObject object) {
                return object;
            }
            throw new GitIntegrationException("provider_error", "GitHub response body is invalid");
        }
    }

    private static JsonArray readArray(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body == null ? "[]" : body))) {
            JsonValue value = reader.readValue();
            if (value instanceof JsonArray array) {
                return array;
            }
            throw new GitIntegrationException("provider_error", "GitHub response body is invalid");
        }
    }

    private static String fieldAsString(JsonObject json, String key) {
        return fieldAsString(json, key, null);
    }

    private static String fieldAsString(JsonObject json, String key, String fallback) {
        JsonValue value = json.get(key);
        if (value == null || value == JsonValue.NULL) {
            return fallback;
        }
        if (value instanceof JsonString string) {
            return string.getString();
        }
        return value.toString();
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new GitIntegrationException("validation_error", fieldName + " is required");
        }
        return value.trim();
    }

    private static String requiredToken(String value) {
        if (value == null || value.trim().isBlank()) {
            throw new GitIntegrationException("provider_error", "GitHub access token is unavailable");
        }
        return value.trim();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @FunctionalInterface
    public interface AccessTokenProvider {
        String accessToken(GitProviderAction action);
    }

    public interface HttpTransport {
        HttpResult send(HttpRequest request, String body) throws IOException, InterruptedException;
    }

    public record HttpResult(int statusCode, String body) {
    }

    private record JavaHttpTransport(HttpClient httpClient) implements HttpTransport {
        @Override
        public HttpResult send(HttpRequest request, String body) throws IOException, InterruptedException {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        }
    }
}
