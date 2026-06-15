package dev.vericov.componentconfig;

import dev.vericov.ignore.CoverageIgnoreRules;
import dev.vericov.ignore.CoveragePathPattern;
import dev.vericov.ignore.InvalidCoverageIgnoreRuleException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record ComponentConfigSnapshot(
        int version,
        List<String> ignore,
        List<ComponentDefinition> components) {

    private static final int MAX_COMPONENTS = 1000;
    private static final int MAX_DEPTH = 20;
    private static final int MAX_PATHS = 100;
    private static final int MAX_PATTERN_LENGTH = 1024;
    private static final int MAX_OWNER_LENGTH = 200;
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,119}");
    private static final Set<String> METRICS = Set.of("line", "branch", "function", "statement");

    public ComponentConfigSnapshot {
        if (version != 1) {
            throw new ComponentConfigException("Only config version 1 is supported");
        }
        try {
            ignore = CoverageIgnoreRules.validate(ignore == null ? List.of() : ignore);
        } catch (InvalidCoverageIgnoreRuleException exception) {
            throw new ComponentConfigException(exception.getMessage(), exception);
        }
        components = List.copyOf(components == null ? List.of() : components);
        ValidationState state = new ValidationState();
        for (int index = 0; index < components.size(); index++) {
            validate(components.get(index), "components[" + index + "]", 0, state);
        }
    }

    public List<ResolvedComponent> resolvedComponents() {
        List<ResolvedComponent> resolved = new ArrayList<>();
        for (int index = 0; index < components.size(); index++) {
            flatten(
                    components.get(index),
                    null,
                    List.of(),
                    List.of(),
                    ComponentGates.empty(),
                    0,
                    index,
                    resolved);
        }
        return List.copyOf(resolved);
    }

    private static void validate(
            ComponentDefinition component,
            String path,
            int depth,
            ValidationState state) {
        if (component == null) {
            throw new ComponentConfigException(path + " must be an object");
        }
        if (depth >= MAX_DEPTH) {
            throw new ComponentConfigException(path + " exceeds maximum component depth " + MAX_DEPTH);
        }
        state.count++;
        if (state.count > MAX_COMPONENTS) {
            throw new ComponentConfigException("components exceed maximum count " + MAX_COMPONENTS);
        }
        if (component.key() == null || !KEY_PATTERN.matcher(component.key()).matches()) {
            throw new ComponentConfigException(path + ".key is invalid");
        }
        if ("unassigned".equals(component.key())) {
            throw new ComponentConfigException(path + ".key uses reserved key unassigned");
        }
        if (!state.keys.add(component.key())) {
            throw new ComponentConfigException(path + ".key is a duplicate component key: " + component.key());
        }
        if (component.name() == null || component.name().isBlank()) {
            throw new ComponentConfigException(path + ".name must be a non-empty string");
        }
        validateOwners(component.owners(), path + ".owners");
        validateGates(component.gates(), path + ".gates");

        boolean hasPaths = !component.paths().isEmpty();
        boolean hasChildren = !component.components().isEmpty();
        if (hasPaths == hasChildren) {
            throw new ComponentConfigException(
                    path + " must define exactly one of non-empty paths or components");
        }
        if (hasPaths) {
            if (component.paths().size() > MAX_PATHS) {
                throw new ComponentConfigException(path + ".paths exceeds maximum path count " + MAX_PATHS);
            }
            Set<String> local = new HashSet<>();
            for (int index = 0; index < component.paths().size(); index++) {
                String pattern = component.paths().get(index);
                String patternPath = path + ".paths[" + index + "]";
                if (pattern == null || pattern.isBlank()) {
                    throw new ComponentConfigException(patternPath + " must be a non-empty string");
                }
                if (pattern.length() > MAX_PATTERN_LENGTH) {
                    throw new ComponentConfigException(patternPath + " exceeds " + MAX_PATTERN_LENGTH + " characters");
                }
                try {
                    new CoveragePathPattern(pattern);
                } catch (InvalidCoverageIgnoreRuleException exception) {
                    throw new ComponentConfigException(patternPath + " " + exception.getMessage(), exception);
                }
                if (!local.add(pattern)) {
                    throw new ComponentConfigException(patternPath + " is a duplicate component path");
                }
                String prior = state.patterns.putIfAbsent(pattern, component.key());
                if (prior != null) {
                    throw new ComponentConfigException(
                            patternPath + " is a duplicate component path from " + prior);
                }
            }
        } else {
            for (int index = 0; index < component.components().size(); index++) {
                validate(
                        component.components().get(index),
                        path + ".components[" + index + "]",
                        depth + 1,
                        state);
            }
        }
    }

    private static void validateOwners(List<String> owners, String path) {
        if (owners == null) {
            return;
        }
        if (owners.isEmpty()) {
            throw new ComponentConfigException(path + " must be a non-empty list");
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < owners.size(); index++) {
            String owner = owners.get(index);
            if (owner == null || owner.isBlank()) {
                throw new ComponentConfigException(path + "[" + index + "] must be a non-empty string");
            }
            if (owner.length() > MAX_OWNER_LENGTH) {
                throw new ComponentConfigException(path + "[" + index + "] exceeds " + MAX_OWNER_LENGTH + " characters");
            }
            if (!seen.add(owner)) {
                throw new ComponentConfigException(path + "[" + index + "] duplicates owner " + owner);
            }
        }
    }

    private static void validateGates(ComponentGates gates, String path) {
        for (Map.Entry<String, BigDecimal> entry : gates.thresholds().entrySet()) {
            if (!METRICS.contains(entry.getKey())) {
                throw new ComponentConfigException(path + " contains unsupported metric " + entry.getKey());
            }
            BigDecimal threshold = entry.getValue();
            if (threshold == null
                    || threshold.compareTo(BigDecimal.ZERO) < 0
                    || threshold.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ComponentConfigException(path + "." + entry.getKey() + " must be between 0 and 100");
            }
        }
    }

    private static void flatten(
            ComponentDefinition component,
            String parentKey,
            List<String> parentPath,
            List<String> inheritedOwners,
            ComponentGates inheritedGates,
            int depth,
            int position,
            List<ResolvedComponent> output) {
        List<String> componentPath = new ArrayList<>(parentPath);
        componentPath.add(component.key());
        List<String> effectiveOwners =
                component.owners() == null ? inheritedOwners : component.owners();
        ComponentGates effectiveGates = inheritedGates.overlay(component.gates());
        output.add(new ResolvedComponent(
                component.key(),
                parentKey,
                componentPath,
                depth,
                position,
                component.name(),
                effectiveOwners,
                effectiveGates.thresholds(),
                component.paths()));
        for (int index = 0; index < component.components().size(); index++) {
            flatten(
                    component.components().get(index),
                    component.key(),
                    componentPath,
                    effectiveOwners,
                    effectiveGates,
                    depth + 1,
                    index,
                    output);
        }
    }

    private static final class ValidationState {
        private final Set<String> keys = new HashSet<>();
        private final Map<String, String> patterns = new HashMap<>();
        private int count;
    }
}
