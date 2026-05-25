package dev.vericov.git.application;

import dev.vericov.git.application.port.CredentialLease;
import dev.vericov.git.application.port.GitActionRepository;
import dev.vericov.git.application.port.GitProviderActionPort;
import dev.vericov.git.application.port.IntegrationConfigClient;
import dev.vericov.git.application.port.ResolvedGitIntegration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class GitProviderActionService {
    private static final String SERVICE_NAME = "git-integration";
    private static final String ACTIVE_STATUS = "active";
    private static final String CHECKS_CAPABILITY = "git.checks";
    private static final String COMMENTS_CAPABILITY = "git.comments";
    private static final String PULL_REQUESTS_CAPABILITY = "git.pull_requests";

    private final IntegrationConfigClient integrationConfigClient;
    private final GitProviderActionPort providerActionPort;
    private final GitActionRepository actionRepository;

    public GitProviderActionService(
            IntegrationConfigClient integrationConfigClient,
            GitProviderActionPort providerActionPort) {
        this(integrationConfigClient, providerActionPort, new InMemoryGitActionRepository());
    }

    public GitProviderActionService(
            IntegrationConfigClient integrationConfigClient,
            GitProviderActionPort providerActionPort,
            GitActionRepository actionRepository) {
        this.integrationConfigClient = Objects.requireNonNull(integrationConfigClient, "integrationConfigClient");
        this.providerActionPort = Objects.requireNonNull(providerActionPort, "providerActionPort");
        this.actionRepository = Objects.requireNonNull(actionRepository, "actionRepository");
    }

    public void createOrUpdateCheckRun(CreateOrUpdateCheckRunCommand command) {
        Objects.requireNonNull(command, "command");
        if (actionRepository.findCheckRunByIdempotencyKey(
                        command.tenantId(),
                        command.repositoryId(),
                        command.providerKey(),
                        command.idempotencyKey())
                .filter(existing -> "completed".equals(existing.status()))
                .isPresent()) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("check_name", command.checkName());
        details.put("commit_sha", command.commitSha());
        details.put("status", command.status());
        details.put("idempotency_key", command.idempotencyKey());
        if (command.conclusion() != null) {
            details.put("conclusion", command.conclusion());
        }
        if (command.summary() != null) {
            details.put("summary", command.summary());
        }
        if (command.text() != null) {
            details.put("text", command.text());
        }
        if (command.detailsUrl() != null) {
            details.put("details_url", command.detailsUrl());
        }
        if (!command.annotations().isEmpty()) {
            details.put("annotations", annotationMaps(command.annotations()));
        }
        GitProviderActionResult result = executeProviderAction(
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                CHECKS_CAPABILITY,
                GitProviderActionType.CREATE_OR_UPDATE_CHECK_RUN,
                details);
        Instant now = Instant.now();
        actionRepository.saveCheckRun(new GitCheckRunDetails(
                UUID.randomUUID(),
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                command.commitSha(),
                command.checkName(),
                result.providerId(),
                command.status(),
                command.conclusion(),
                command.detailsUrl(),
                Map.of(
                        "summary", command.summary() == null ? "" : command.summary(),
                        "text", command.text() == null ? "" : command.text()),
                command.idempotencyKey(),
                now,
                now));
    }

    public void createOrUpdatePrComment(CreateOrUpdatePrCommentCommand command) {
        Objects.requireNonNull(command, "command");
        GitProviderActionResult result = executeProviderAction(
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                COMMENTS_CAPABILITY,
                GitProviderActionType.CREATE_OR_UPDATE_PR_COMMENT,
                Map.of(
                        "pull_request_number", command.pullRequestNumber(),
                        "marker", command.marker(),
                        "body", command.body()));
        Instant now = Instant.now();
        actionRepository.savePrComment(new GitPrCommentDetails(
                UUID.randomUUID(),
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                command.pullRequestNumber(),
                command.marker(),
                result.providerId(),
                sha256Hex(command.body()),
                result.status(),
                result.providerUrl(),
                now,
                now));
    }

    public void createOrUpdatePrAnnotations(CreateOrUpdatePrAnnotationsCommand command) {
        Objects.requireNonNull(command, "command");
        executeProviderAction(
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                PULL_REQUESTS_CAPABILITY,
                GitProviderActionType.CREATE_OR_UPDATE_PR_ANNOTATIONS,
                Map.of(
                        "pull_request_number", command.pullRequestNumber(),
                        "annotation_batch_key", command.annotationBatchKey(),
                        "annotations", annotationMaps(command.annotations())));
    }

    public void createBranch(CreateBranchCommand command) {
        Objects.requireNonNull(command, "command");
        if (actionRepository.findBranchByIdempotencyKey(
                        command.tenantId(),
                        command.repositoryId(),
                        command.providerKey(),
                        command.idempotencyKey())
                .filter(existing -> "created".equals(existing.status()) || "already_exists".equals(existing.status()))
                .isPresent()) {
            return;
        }
        GitProviderActionResult result = executeProviderAction(
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                PULL_REQUESTS_CAPABILITY,
                GitProviderActionType.CREATE_BRANCH,
                Map.of(
                        "branch_name", command.branchName(),
                        "base_sha", command.baseSha(),
                        "idempotency_key", command.idempotencyKey()));
        Instant now = Instant.now();
        actionRepository.saveBranch(new GitBranchDetails(
                UUID.randomUUID(),
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                command.branchName(),
                command.baseSha(),
                result.providerId(),
                result.status(),
                command.idempotencyKey(),
                now,
                now));
    }

    public void openPullRequest(OpenPullRequestCommand command) {
        Objects.requireNonNull(command, "command");
        executeProviderAction(
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                PULL_REQUESTS_CAPABILITY,
                GitProviderActionType.OPEN_PULL_REQUEST,
                Map.of(
                        "source_branch", command.sourceBranch(),
                        "target_branch", command.targetBranch(),
                        "title", command.title(),
                        "body", command.body(),
                        "draft", command.draft(),
                        "idempotency_key", command.idempotencyKey()));
    }

    private GitProviderActionResult executeProviderAction(
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String requiredCapability,
            GitProviderActionType actionType,
            Map<String, Object> details) {
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        String normalizedProviderKey = GitValues.requireCanonical(providerKey, "provider_key is required");

        ResolvedGitIntegration resolved = integrationConfigClient.resolveRepositoryIntegration(
                tenantId,
                orgId,
                repositoryId,
                normalizedProviderKey,
                requiredCapability);
        if (resolved == null) {
            throw new GitIntegrationException("not_found", "Integration resolution not found");
        }
        verifyResolvedBinding(resolved, tenantId, orgId, repositoryId, normalizedProviderKey, requiredCapability);
        String credentialKind = GitValues.requireCanonical(
                resolved.credentialKind(),
                "credential_kind is required");

        CredentialLease credentialLease = integrationConfigClient.leaseCredential(
                tenantId,
                orgId,
                resolved.connectionId(),
                credentialKind,
                SERVICE_NAME);
        if (credentialLease == null) {
            throw new GitIntegrationException("not_found", "Integration credential lease not found");
        }
        if (!credentialKind.equals(credentialLease.credentialKind())) {
            throw new GitIntegrationException("validation_error", "Integration credential lease kind does not match resolution");
        }

        return providerActionPort.execute(new GitProviderAction(
                actionType,
                tenantId,
                orgId,
                repositoryId,
                resolved.connectionId(),
                normalizedProviderKey,
                resolved.externalRepositoryId(),
                requiredCapability,
                credentialKind,
                credentialLease,
                resolved.connectionConfig(),
                resolved.bindingConfig(),
                details));
    }

    private static List<Map<String, Object>> annotationMaps(List<GitAnnotationInput> annotations) {
        return annotations.stream()
                .map(annotation -> Map.<String, Object>of(
                        "path", annotation.path(),
                        "start_line", annotation.startLine(),
                        "end_line", annotation.endLine(),
                        "annotation_level", annotation.annotationLevel(),
                        "message", annotation.message()))
                .toList();
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void verifyResolvedBinding(
            ResolvedGitIntegration resolved,
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String requiredCapability) {
        if (!tenantId.equals(resolved.tenantId())
                || !orgId.equals(resolved.orgId())
                || !repositoryId.equals(resolved.repositoryId())
                || !providerKey.equals(resolved.providerKey())) {
            throw new GitIntegrationException("validation_error", "Integration resolution does not match request");
        }
        if (!ACTIVE_STATUS.equals(resolved.connectionStatus())) {
            throw new GitIntegrationException("not_found", "Active integration connection not found");
        }
        if (!ACTIVE_STATUS.equals(resolved.bindingStatus())) {
            throw new GitIntegrationException("not_found", "Active integration binding not found");
        }
        if (!resolved.grantsCapability(requiredCapability)) {
            throw new GitIntegrationException(
                    "forbidden",
                    "Integration binding does not grant " + requiredCapability);
        }
    }
}
