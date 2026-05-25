package dev.vericov.git.application;

import dev.vericov.git.application.port.GitActionRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGitActionRepository implements GitActionRepository {
    private final Map<WebhookKey, GitWebhookEventDetails> webhookEventsByKey = new ConcurrentHashMap<>();
    private final Map<PullRequestKey, GitPullRequestDetails> pullRequestsByKey = new ConcurrentHashMap<>();
    private final Map<ActionKey, GitCheckRunDetails> checkRunsByKey = new ConcurrentHashMap<>();
    private final Map<CommentKey, GitPrCommentDetails> commentsByKey = new ConcurrentHashMap<>();
    private final Map<ActionKey, GitBranchDetails> branchesByKey = new ConcurrentHashMap<>();

    @Override
    public Optional<GitWebhookEventDetails> findWebhookEvent(String providerKey, String deliveryId) {
        return Optional.ofNullable(webhookEventsByKey.get(new WebhookKey(
                GitValues.requireCanonical(providerKey, "provider_key is required"),
                GitValues.requireTrimmed(deliveryId, "delivery_id is required"))));
    }

    @Override
    public GitWebhookEventDetails saveWebhookEvent(GitWebhookEventDetails details) {
        webhookEventsByKey.put(new WebhookKey(details.providerKey(), details.deliveryId()), details);
        return details;
    }

    @Override
    public Optional<GitPullRequestDetails> findPullRequest(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            int pullRequestNumber) {
        return Optional.ofNullable(pullRequestsByKey.get(new PullRequestKey(
                tenantId,
                repositoryId,
                GitValues.requireCanonical(providerKey, "provider_key is required"),
                pullRequestNumber)));
    }

    @Override
    public GitPullRequestDetails savePullRequest(GitPullRequestDetails details) {
        pullRequestsByKey.put(new PullRequestKey(
                details.tenantId(),
                details.repositoryId(),
                details.providerKey(),
                details.number()), details);
        return details;
    }

    @Override
    public Optional<GitCheckRunDetails> findCheckRunByIdempotencyKey(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            String idempotencyKey) {
        return Optional.ofNullable(checkRunsByKey.get(new ActionKey(
                tenantId,
                repositoryId,
                GitValues.requireCanonical(providerKey, "provider_key is required"),
                GitValues.requireTrimmed(idempotencyKey, "idempotency_key is required"))));
    }

    @Override
    public GitCheckRunDetails saveCheckRun(GitCheckRunDetails details) {
        checkRunsByKey.put(new ActionKey(
                details.tenantId(),
                details.repositoryId(),
                details.providerKey(),
                details.idempotencyKey()), details);
        return details;
    }

    @Override
    public Optional<GitPrCommentDetails> findPrComment(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            int pullRequestNumber,
            String commentKey) {
        return Optional.ofNullable(commentsByKey.get(new CommentKey(
                tenantId,
                repositoryId,
                GitValues.requireCanonical(providerKey, "provider_key is required"),
                pullRequestNumber,
                GitValues.requireTrimmed(commentKey, "comment_key is required"))));
    }

    @Override
    public GitPrCommentDetails savePrComment(GitPrCommentDetails details) {
        commentsByKey.put(new CommentKey(
                details.tenantId(),
                details.repositoryId(),
                details.providerKey(),
                details.pullRequestNumber(),
                details.commentKey()), details);
        return details;
    }

    @Override
    public Optional<GitBranchDetails> findBranchByIdempotencyKey(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            String idempotencyKey) {
        return Optional.ofNullable(branchesByKey.get(new ActionKey(
                tenantId,
                repositoryId,
                GitValues.requireCanonical(providerKey, "provider_key is required"),
                GitValues.requireTrimmed(idempotencyKey, "idempotency_key is required"))));
    }

    @Override
    public GitBranchDetails saveBranch(GitBranchDetails details) {
        branchesByKey.put(new ActionKey(
                details.tenantId(),
                details.repositoryId(),
                details.providerKey(),
                details.idempotencyKey()), details);
        return details;
    }

    private record ActionKey(UUID tenantId, UUID repositoryId, String providerKey, String idempotencyKey) {
    }

    private record WebhookKey(String providerKey, String deliveryId) {
    }

    private record PullRequestKey(UUID tenantId, UUID repositoryId, String providerKey, int pullRequestNumber) {
    }

    private record CommentKey(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            int pullRequestNumber,
            String commentKey) {
    }
}
