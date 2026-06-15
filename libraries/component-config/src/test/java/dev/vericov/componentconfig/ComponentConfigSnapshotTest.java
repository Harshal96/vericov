package dev.vericov.componentconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentConfigSnapshotTest {
    @Test
    void resolvesHierarchyMetadataAndInheritance() {
        ComponentConfigSnapshot snapshot = nestedSnapshot();

        List<ResolvedComponent> resolved = snapshot.resolvedComponents();

        assertEquals(List.of("commerce", "payments", "payments-api"), resolved.stream()
                .map(ResolvedComponent::key)
                .toList());
        ResolvedComponent leaf = resolved.getLast();
        assertEquals("payments", leaf.parentKey());
        assertEquals(List.of("commerce", "payments", "payments-api"), leaf.componentPath());
        assertEquals(2, leaf.depth());
        assertEquals(List.of("team-payments"), leaf.owners());
        assertEquals(
                Map.of("branch", BigDecimal.valueOf(70), "line", BigDecimal.valueOf(90)),
                leaf.effectiveGates());
    }

    @Test
    void rejectsDuplicateKeysAndPatterns() {
        ComponentDefinition first = leaf("same", "src/**");
        ComponentDefinition duplicateKey = leaf("same", "other/**");
        ComponentDefinition duplicatePattern = leaf("other", "src/**");

        assertThrows(
                ComponentConfigException.class,
                () -> new ComponentConfigSnapshot(1, List.of(), List.of(first, duplicateKey)));
        assertThrows(
                ComponentConfigException.class,
                () -> new ComponentConfigSnapshot(1, List.of(), List.of(first, duplicatePattern)));
    }

    @Test
    void resolverUsesSpecificityAndRejectsTies() {
        ComponentConfigSnapshot specific = new ComponentConfigSnapshot(
                1,
                List.of(),
                List.of(leaf("services", "services/**"), leaf("payments", "services/payments/**")));

        assertEquals(
                "payments",
                new ComponentResolver(specific)
                        .resolve("services/payments/api/App.java")
                        .orElseThrow()
                        .leafComponentKey());

        ComponentConfigSnapshot ambiguous = new ComponentConfigSnapshot(
                1,
                List.of(),
                List.of(leaf("first", "src/A*.java"), leaf("second", "src/A?.java")));
        assertThrows(
                ComponentConfigException.class,
                () -> new ComponentResolver(ambiguous).resolve("src/Ab.java"));
    }

    static ComponentConfigSnapshot nestedSnapshot() {
        ComponentDefinition leaf = new ComponentDefinition(
                "payments-api",
                null,
                List.of("team-payments"),
                ComponentGates.empty(),
                List.of("services/payments/api/**"),
                List.of());
        ComponentDefinition payments = new ComponentDefinition(
                "payments",
                null,
                null,
                new ComponentGates(Map.of("line", BigDecimal.valueOf(90))),
                List.of(),
                List.of(leaf));
        ComponentDefinition commerce = new ComponentDefinition(
                "commerce",
                "Commerce",
                List.of("team-commerce"),
                new ComponentGates(Map.of(
                        "line", BigDecimal.valueOf(80),
                        "branch", BigDecimal.valueOf(70))),
                List.of(),
                List.of(payments));
        return new ComponentConfigSnapshot(1, List.of(), List.of(commerce));
    }

    private static ComponentDefinition leaf(String key, String path) {
        return new ComponentDefinition(
                key,
                null,
                null,
                ComponentGates.empty(),
                List.of(path),
                List.of());
    }
}
