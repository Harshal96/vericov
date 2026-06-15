package dev.vericov.componentconfig;

import dev.vericov.ignore.CoveragePathPattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ComponentResolver {
    private final List<LeafPattern> patterns;

    public ComponentResolver(ComponentConfigSnapshot snapshot) {
        List<LeafPattern> compiled = new ArrayList<>();
        for (ResolvedComponent component : snapshot.resolvedComponents()) {
            if (!component.leaf()) {
                continue;
            }
            for (String value : component.paths()) {
                compiled.add(new LeafPattern(component, new CoveragePathPattern(value)));
            }
        }
        patterns = List.copyOf(compiled);
    }

    public Optional<ComponentAssignment> resolve(String filePath) {
        List<LeafPattern> matches = patterns.stream()
                .filter(candidate -> candidate.pattern().matches(filePath))
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        CoveragePathPattern.Specificity highest = matches.stream()
                .map(candidate -> candidate.pattern().specificity())
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<ResolvedComponent> winners = matches.stream()
                .filter(candidate -> candidate.pattern().specificity().equals(highest))
                .map(LeafPattern::component)
                .distinct()
                .toList();
        if (winners.size() > 1) {
            String keys = winners.stream()
                    .map(ResolvedComponent::key)
                    .sorted()
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            throw new ComponentConfigException(
                    "Ambiguous component assignment for " + filePath + ": " + keys);
        }
        ResolvedComponent winner = winners.getFirst();
        return Optional.of(new ComponentAssignment(
                winner.key(),
                winner.componentPath(),
                winner.owners()));
    }

    private record LeafPattern(ResolvedComponent component, CoveragePathPattern pattern) {
    }
}
