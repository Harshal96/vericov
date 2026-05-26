package dev.vericov.agent.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class AgentValues {
    private static final Set<String> TASK_TYPES = Set.of("explain_gap", "generate_tests");
    private static final Set<String> MODES = Set.of("suggest", "dry_run", "open_pr");
    private static final Set<String> RISK_LEVELS = Set.of("critical", "high", "medium", "low");
    private static final Set<String> REQUESTER_TYPES = Set.of("system", "user", "policy", "slash_command", "gate");
    private static final Set<String> DECISIONS = Set.of("allow", "deny", "force_dry_run", "require_approval");
    private static final Set<String> SOURCE_BEARING_KEYS = Set.of(
            "source_text",
            "source_snippet",
            "raw_diff",
            "raw_diff_text",
            "diff_text",
            "patch",
            "patch_content",
            "generated_patch",
            "api_key",
            "access_token",
            "refresh_token",
            "authorization",
            "password",
            "secret",
            "secrets",
            "token",
            "test_output");

    private AgentValues() {
    }

    public static UUID requireUuid(UUID value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required");
    }

    public static String requireTrimmed(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new AgentRunnerException("validation_error", message);
        }
        return value.trim();
    }

    public static String optionalTrimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String requireCanonical(String value, String message) {
        return requireTrimmed(value, message).toLowerCase(Locale.ROOT);
    }

    public static String requireTaskType(String value) {
        String canonical = requireCanonical(value, "task_type is required");
        if (!TASK_TYPES.contains(canonical)) {
            throw new AgentRunnerException("validation_error", "task_type is unsupported");
        }
        return canonical;
    }

    public static String requireMode(String value) {
        String canonical = requireCanonical(value, "mode is required");
        if (!MODES.contains(canonical)) {
            throw new AgentRunnerException("validation_error", "mode is unsupported");
        }
        return canonical;
    }

    public static String requireRiskLevel(String value) {
        String canonical = requireCanonical(value, "risk_level is required");
        if (!RISK_LEVELS.contains(canonical)) {
            throw new AgentRunnerException("validation_error", "risk_level is unsupported");
        }
        return canonical;
    }

    public static String requireRequesterType(String value) {
        String canonical = requireCanonical(value, "requested_by.type is required");
        if (!REQUESTER_TYPES.contains(canonical)) {
            throw new AgentRunnerException("validation_error", "requested_by.type is unsupported");
        }
        return canonical;
    }

    public static String requireDecision(String value) {
        String canonical = requireCanonical(value, "decision is required");
        if (!DECISIONS.contains(canonical)) {
            throw new AgentRunnerException("validation_error", "decision is unsupported");
        }
        return canonical;
    }

    public static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new AgentRunnerException("validation_error", fieldName + " must be positive");
        }
        return value;
    }

    public static <T> List<T> requireNonEmptyList(List<T> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new AgentRunnerException("validation_error", fieldName + " is required");
        }
        return List.copyOf(values);
    }

    public static List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copy.add(value.trim());
            }
        }
        return List.copyOf(copy);
    }

    public static Map<String, Object> copyMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                copy.put(entry.getKey().trim(), immutableValue(entry.getValue()));
            }
        }
        return Map.copyOf(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() != null) {
                    copy.put(key, immutableValue(entry.getValue()));
                }
            }
            return Map.copyOf(copy);
        }
        if (value instanceof List<?> list) {
            return List.copyOf(list.stream().map(AgentValues::immutableValue).toList());
        }
        return value;
    }

    public static void rejectSourceBearingMetadata(Map<String, Object> metadata) {
        rejectSourceBearingValue(metadata, "evidence.metadata");
    }

    @SuppressWarnings("unchecked")
    private static void rejectSourceBearingValue(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    String canonicalKey = key.trim().toLowerCase(Locale.ROOT);
                    if (SOURCE_BEARING_KEYS.contains(canonicalKey)) {
                        throw new AgentRunnerException(
                                "validation_error",
                                path + "." + key + " may contain source-bearing or secret material");
                    }
                }
                rejectSourceBearingValue(entry.getValue(), path + "." + entry.getKey());
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                rejectSourceBearingValue(item, path + "[]");
            }
        }
    }
}
