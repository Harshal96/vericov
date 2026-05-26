package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryCoverageContextDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryCoverageContextHttpResponse(
        @JsonbProperty("context_version")
        String contextVersion,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("commit_sha")
        String commitSha,
        List<RepositoryComponentHttpResponse> components,
        @JsonbProperty("owner_rules")
        List<RepositoryOwnerRuleHttpResponse> ownerRules,
        @JsonbProperty("package_nodes")
        List<RepositoryPackageNodeHttpResponse> packageNodes,
        @JsonbProperty("policy_defaults")
        Map<String, Object> policyDefaults,
        @JsonbProperty("generated_at")
        Instant generatedAt) {

    public static RepositoryCoverageContextHttpResponse from(RepositoryCoverageContextDetails details) {
        return new RepositoryCoverageContextHttpResponse(
                details.contextVersion(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.commitSha(),
                details.components().stream().map(RepositoryComponentHttpResponse::from).toList(),
                details.ownerRules().stream().map(RepositoryOwnerRuleHttpResponse::from).toList(),
                details.packageNodes().stream().map(RepositoryPackageNodeHttpResponse::from).toList(),
                details.policyDefaults(),
                details.generatedAt());
    }
}
