package dev.vericov.integrations.config;

import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.ProviderDefinition;
import dev.vericov.integrations.application.port.ProviderRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StaticProviderRegistry implements ProviderRegistry {
    private static final List<String> GIT_CAPABILITIES = List.of(
            "git.webhooks",
            "git.checks",
            "git.comments",
            "git.pull_requests",
            "git.repository_sync");
    private static final Map<String, String> GITHUB_CREDENTIAL_KINDS = Map.of(
            "git.webhooks", "webhook_secret",
            "git.checks", "github_app_private_key",
            "git.comments", "github_app_private_key",
            "git.pull_requests", "github_app_private_key",
            "git.repository_sync", "github_app_private_key");
    private static final Map<String, String> OAUTH_GIT_CREDENTIAL_KINDS = Map.of(
            "git.webhooks", "webhook_secret",
            "git.checks", "oauth_access_token",
            "git.comments", "oauth_access_token",
            "git.pull_requests", "oauth_access_token",
            "git.repository_sync", "oauth_access_token");

    private final List<ProviderDefinition> providers;
    private final Map<String, ProviderDefinition> providersByKey;

    public StaticProviderRegistry(List<ProviderDefinition> providers) {
        this.providers = List.copyOf(providers == null ? List.of() : providers);
        Map<String, ProviderDefinition> byKey = new LinkedHashMap<>();
        for (ProviderDefinition provider : this.providers) {
            String providerKey = normalizeProviderKey(provider.providerKey());
            if (byKey.containsKey(providerKey)) {
                throw new IllegalArgumentException("Duplicate integration provider key: " + providerKey);
            }
            byKey.put(providerKey, provider);
        }
        providersByKey = Map.copyOf(byKey);
    }

    public static StaticProviderRegistry defaultRegistry() {
        return new StaticProviderRegistry(List.of(
                new ProviderDefinition(
                        "github",
                        "git",
                        "GitHub",
                        "github_app",
                        GIT_CAPABILITIES,
                        Map.of("webhook_events", List.of("pull_request", "check_suite", "issue_comment")),
                        GITHUB_CREDENTIAL_KINDS),
                new ProviderDefinition(
                        "gitlab",
                        "git",
                        "GitLab",
                        "oauth_app",
                        GIT_CAPABILITIES,
                        Map.of("webhook_events", List.of("merge_request", "pipeline", "note")),
                        OAUTH_GIT_CREDENTIAL_KINDS),
                new ProviderDefinition(
                        "bitbucket",
                        "git",
                        "Bitbucket",
                        "oauth_app",
                        GIT_CAPABILITIES,
                        Map.of("webhook_events", List.of("pullrequest:created", "pullrequest:updated", "repo:push")),
                        OAUTH_GIT_CREDENTIAL_KINDS),
                new ProviderDefinition(
                        "slack",
                        "chat",
                        "Slack",
                        "oauth_app",
                        List.of("chat.notifications"),
                        Map.of(),
                        Map.of("chat.notifications", "oauth_access_token")),
                new ProviderDefinition(
                        "jira",
                        "issue_tracker",
                        "Jira",
                        "oauth_app",
                        List.of("issues.read", "issues.comments"),
                        Map.of(),
                        Map.of("issues.read", "oauth_access_token", "issues.comments", "oauth_access_token")),
                new ProviderDefinition(
                        "linear",
                        "issue_tracker",
                        "Linear",
                        "oauth_app",
                        List.of("issues.read", "issues.comments"),
                        Map.of(),
                        Map.of("issues.read", "oauth_access_token", "issues.comments", "oauth_access_token")),
                new ProviderDefinition(
                        "openai",
                        "ai",
                        "OpenAI",
                        "api_key",
                        List.of("ai.completions"),
                        Map.of(),
                        Map.of("ai.completions", "api_token"))));
    }

    @Override
    public List<ProviderDefinition> listProviders(String type) {
        if (type == null || type.isBlank()) {
            return providers;
        }
        String normalizedType = type.trim().toLowerCase(Locale.ROOT);
        return providers.stream()
                .filter(provider -> provider.type().equals(normalizedType))
                .toList();
    }

    @Override
    public Optional<ProviderDefinition> findProvider(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providersByKey.get(normalizeProviderKey(providerKey)));
    }

    @Override
    public ProviderDefinition requireProvider(String providerKey) {
        return findProvider(providerKey)
                .orElseThrow(() -> new IntegrationException("not_found", "Integration provider not found"));
    }

    private static String normalizeProviderKey(String providerKey) {
        return Objects.requireNonNull(providerKey, "providerKey").trim().toLowerCase(Locale.ROOT);
    }
}
