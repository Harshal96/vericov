package dev.vericov.git.application.port;

import dev.vericov.git.application.GitBranchDetails;
import dev.vericov.git.application.GitCheckRunDetails;
import dev.vericov.git.application.GitPrCommentDetails;
import dev.vericov.git.application.GitPullRequestDetails;
import dev.vericov.git.application.GitWebhookEventDetails;
import java.util.Optional;
import java.util.UUID;

public interface GitActionRepository {
    Optional<GitWebhookEventDetails> findWebhookEvent(String providerKey, String deliveryId);

    GitWebhookEventDetails saveWebhookEvent(GitWebhookEventDetails details);

    Optional<GitPullRequestDetails> findPullRequest(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            int pullRequestNumber);

    GitPullRequestDetails savePullRequest(GitPullRequestDetails details);

    Optional<GitCheckRunDetails> findCheckRunByIdempotencyKey(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            String idempotencyKey);

    GitCheckRunDetails saveCheckRun(GitCheckRunDetails details);

    Optional<GitPrCommentDetails> findPrComment(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            int pullRequestNumber,
            String commentKey);

    GitPrCommentDetails savePrComment(GitPrCommentDetails details);

    Optional<GitBranchDetails> findBranchByIdempotencyKey(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            String idempotencyKey);

    GitBranchDetails saveBranch(GitBranchDetails details);
}
