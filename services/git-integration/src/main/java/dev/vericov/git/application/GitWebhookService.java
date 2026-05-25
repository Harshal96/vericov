package dev.vericov.git.application;

import dev.vericov.git.application.port.GitActionRepository;
import dev.vericov.git.application.port.GitEventPublisher;
import dev.vericov.git.application.port.GitWebhookVerifier;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class GitWebhookService {
    private final GitActionRepository repository;
    private final GitWebhookVerifier verifier;
    private final GitEventPublisher eventPublisher;

    public GitWebhookService(
            GitActionRepository repository,
            GitWebhookVerifier verifier,
            GitEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public GitWebhookProcessingResult receive(GitWebhookCommand command) {
        Objects.requireNonNull(command, "command");
        byte[] payload = command.payload();
        if (!verifier.verify(
                command.providerKey(),
                command.eventType(),
                command.deliveryId(),
                command.signature(),
                payload)) {
            throw new GitIntegrationException("unauthorized", "Webhook signature verification failed");
        }

        return repository.findWebhookEvent(command.providerKey(), command.deliveryId())
                .map(existing -> new GitWebhookProcessingResult(
                        existing.id(),
                        existing.providerKey(),
                        existing.deliveryId(),
                        "duplicate"))
                .orElseGet(() -> processNewEvent(command, payload));
    }

    private GitWebhookProcessingResult processNewEvent(GitWebhookCommand command, byte[] payloadBytes) {
        Instant processedAt = Instant.now();
        String body = new String(payloadBytes, StandardCharsets.UTF_8);
        JsonObject payloadJson = readObject(body);
        Map<String, Object> payload = toMap(payloadJson);
        Map<String, Object> normalizedPayload = normalize(command.providerKey(), command.eventType(), payloadJson);
        GitWebhookEventDetails details = new GitWebhookEventDetails(
                UUID.randomUUID(),
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.connectionId(),
                command.webhookEndpointId(),
                command.providerKey(),
                command.eventType(),
                command.deliveryId(),
                true,
                sha256Hex(payloadBytes),
                payload,
                normalizedPayload,
                "processed",
                Map.of(),
                command.receivedAt(),
                processedAt,
                command.receivedAt(),
                processedAt);
        GitWebhookEventDetails saved = repository.saveWebhookEvent(details);
        savePullRequestIfPresent(saved);
        eventPublisher.publish(new PublishedGitEvent(
                saved.tenantId(),
                saved.orgId(),
                saved.repositoryId(),
                saved.connectionId(),
                saved.providerKey(),
                "git.webhook." + saved.eventType(),
                saved.deliveryId(),
                saved.normalizedPayload(),
                saved.receivedAt(),
                saved.processedAt()));
        return new GitWebhookProcessingResult(saved.id(), saved.providerKey(), saved.deliveryId(), saved.status());
    }

    private void savePullRequestIfPresent(GitWebhookEventDetails event) {
        if (!"pull_request".equals(event.eventType())
                || event.tenantId() == null
                || event.orgId() == null
                || event.repositoryId() == null) {
            return;
        }
        Map<String, Object> normalized = event.normalizedPayload();
        Integer number = optionalInteger(normalized.get("pull_request_number"));
        String providerId = optionalString(normalized.get("pull_request_provider_id"));
        String title = optionalString(normalized.get("pull_request_title"));
        String author = optionalString(normalized.get("pull_request_author"));
        String baseBranch = optionalString(normalized.get("base_branch"));
        String baseSha = optionalString(normalized.get("base_sha"));
        String headBranch = optionalString(normalized.get("head_branch"));
        String headSha = optionalString(normalized.get("head_sha"));
        String state = optionalString(normalized.get("pull_request_state"));
        if (number == null
                || providerId == null
                || title == null
                || author == null
                || baseBranch == null
                || baseSha == null
                || headBranch == null
                || headSha == null
                || state == null) {
            return;
        }
        repository.savePullRequest(new GitPullRequestDetails(
                UUID.randomUUID(),
                event.tenantId(),
                event.orgId(),
                event.repositoryId(),
                event.providerKey(),
                providerId,
                number,
                title,
                author,
                baseBranch,
                baseSha,
                headBranch,
                headSha,
                state,
                optionalString(normalized.get("pull_request_url")),
                event.receivedAt(),
                event.processedAt()));
    }

    private static Map<String, Object> normalize(String providerKey, String eventType, JsonObject payload) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("provider_key", providerKey);
        normalized.put("event_type", eventType);
        putIfPresent(normalized, "action", payload.getString("action", null));
        JsonObject repository = payload.getJsonObject("repository");
        if (repository != null) {
            putIfPresent(normalized, "repository_provider_id", jsonValueToScalar(repository.get("id")));
            putIfPresent(normalized, "repository_full_name", repository.getString("full_name", null));
        }
        JsonObject installation = payload.getJsonObject("installation");
        if (installation != null) {
            putIfPresent(normalized, "installation_id", jsonValueToScalar(installation.get("id")));
        }
        JsonObject pullRequest = payload.getJsonObject("pull_request");
        if (pullRequest != null) {
            putIfPresent(normalized, "pull_request_provider_id", jsonValueToScalar(pullRequest.get("id")));
            putIfPresent(normalized, "pull_request_number", jsonValueToScalar(pullRequest.get("number")));
            putIfPresent(normalized, "pull_request_state", pullRequest.getString("state", null));
            putIfPresent(normalized, "pull_request_url", pullRequest.getString("html_url", null));
            putIfPresent(normalized, "pull_request_title", pullRequest.getString("title", null));
            JsonObject user = pullRequest.getJsonObject("user");
            if (user != null) {
                putIfPresent(normalized, "pull_request_author", user.getString("login", null));
            }
            JsonObject head = pullRequest.getJsonObject("head");
            if (head != null) {
                putIfPresent(normalized, "head_branch", head.getString("ref", null));
                putIfPresent(normalized, "head_sha", head.getString("sha", null));
            }
            JsonObject base = pullRequest.getJsonObject("base");
            if (base != null) {
                putIfPresent(normalized, "base_branch", base.getString("ref", null));
                putIfPresent(normalized, "base_sha", base.getString("sha", null));
            }
        }
        JsonObject checkRun = payload.getJsonObject("check_run");
        if (checkRun != null) {
            putIfPresent(normalized, "check_run_provider_id", jsonValueToScalar(checkRun.get("id")));
            putIfPresent(normalized, "check_run_name", checkRun.getString("name", null));
            putIfPresent(normalized, "check_run_status", checkRun.getString("status", null));
            putIfPresent(normalized, "check_run_conclusion", checkRun.getString("conclusion", null));
        }
        JsonObject comment = payload.getJsonObject("comment");
        if (comment != null) {
            putIfPresent(normalized, "comment_provider_id", jsonValueToScalar(comment.get("id")));
            putIfPresent(normalized, "comment_url", comment.getString("html_url", null));
        }
        return normalized;
    }

    private static void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private static JsonObject readObject(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonValue value = reader.readValue();
            if (value instanceof JsonObject object) {
                return object;
            }
            throw new GitIntegrationException("validation_error", "Webhook payload must be a JSON object");
        }
    }

    private static Map<String, Object> toMap(JsonObject object) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            values.put(entry.getKey(), jsonValueToScalar(entry.getValue()));
        }
        return values;
    }

    private static List<Object> toList(JsonArray array) {
        List<Object> values = new ArrayList<>();
        for (JsonValue value : array) {
            values.add(jsonValueToScalar(value));
        }
        return List.copyOf(values);
    }

    private static Object jsonValueToScalar(JsonValue value) {
        if (value == null || value == JsonValue.NULL) {
            return null;
        }
        return switch (value.getValueType()) {
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> numberValue((JsonNumber) value);
            case TRUE -> true;
            case FALSE -> false;
            case OBJECT -> toMap(value.asJsonObject());
            case ARRAY -> toList(value.asJsonArray());
            case NULL -> null;
        };
    }

    private static Object numberValue(JsonNumber value) {
        if (value.isIntegral()) {
            try {
                return value.intValueExact();
            } catch (ArithmeticException exception) {
                return value.longValue();
            }
        }
        return value.bigDecimalValue();
    }

    private static String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private static Integer optionalInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static String sha256Hex(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
