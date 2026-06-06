package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryCoverageContextDetails(
        String contextVersion,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String commitSha,
        List<RepositoryComponentDetails> components,
        List<RepositoryOwnerRuleDetails> ownerRules,
        List<RepositoryPackageNodeDetails> packageNodes,
        Map<String, Object> policyDefaults,
        Map<String, Object> riskConfig,
        Instant generatedAt) {

    public RepositoryCoverageContextDetails {
        components = List.copyOf(components == null ? List.of() : components);
        ownerRules = List.copyOf(ownerRules == null ? List.of() : ownerRules);
        packageNodes = List.copyOf(packageNodes == null ? List.of() : packageNodes);
        policyDefaults = ConfigurationValues.deepCopyMap(policyDefaults);
        riskConfig = ConfigurationValues.deepCopyMap(riskConfig);
    }
}
