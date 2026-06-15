package dev.vericov.upload.application;

import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.RunnerUploadTokenIssuer;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.UploadStatus;
import dev.vericov.ignore.CoverageIgnoreRules;
import dev.vericov.ignore.InvalidCoverageIgnoreRuleException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UploadApplicationService {
    private static final String CREATE_UPLOAD_SCOPE = "uploads:create";
    private static final String READ_UPLOAD_SCOPE = "uploads:read";
    private static final Duration RUNNER_UPLOAD_TOKEN_TTL = Duration.ofMinutes(15);

    private final RepositoryApiKeyAuthenticator authenticator;
    private final UploadRepository uploadRepository;
    private final ArtifactStorage artifactStorage;
    private final UploadEventPublisher eventPublisher;
    private final UploadWorkQueue workQueue;
    private final Clock clock;
    private final RunnerUploadTokenIssuer runnerTokenIssuer;

    public UploadApplicationService(
            RepositoryApiKeyAuthenticator authenticator,
            UploadRepository uploadRepository,
            ArtifactStorage artifactStorage,
            UploadEventPublisher eventPublisher,
            UploadWorkQueue workQueue,
            Clock clock) {
        this(authenticator, uploadRepository, artifactStorage, eventPublisher, workQueue, clock, null);
    }

    public UploadApplicationService(
            RepositoryApiKeyAuthenticator authenticator,
            UploadRepository uploadRepository,
            ArtifactStorage artifactStorage,
            UploadEventPublisher eventPublisher,
            UploadWorkQueue workQueue,
            Clock clock,
            RunnerUploadTokenIssuer runnerTokenIssuer) {
        this.authenticator = authenticator;
        this.uploadRepository = uploadRepository;
        this.artifactStorage = artifactStorage;
        this.eventPublisher = eventPublisher;
        this.workQueue = workQueue;
        this.clock = clock;
        this.runnerTokenIssuer = runnerTokenIssuer;
    }

    public UploadAccepted acceptUpload(CreateUploadCommand command) {
        validate(command);
        RepositoryApiKeyPrincipal principal = authenticator.authenticate(command);
        CreateUploadCommand resolvedCommand = resolveRepository(command, principal);
        authorize(principal, resolvedCommand);

        return uploadRepository.findByIdempotencyKey(resolvedCommand.repositoryId(), resolvedCommand.idempotencyKey())
                .map(this::toAccepted)
                .orElseGet(() -> acceptNewUpload(resolvedCommand, principal));
    }

    public UploadDetails getUpload(UUID uploadId) {
        QueuedUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new InvalidUploadException("not_found", "Upload not found"));
        return uploadDetails(upload);
    }

    public UploadDetails getUpload(UUID uploadId, String authorizationHeader) {
        QueuedUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new InvalidUploadException("not_found", "Upload not found"));
        RepositoryApiKeyPrincipal principal = authenticator.authenticateRepositoryAccess(
                authorizationHeader,
                upload.repositoryId(),
                upload.branch());
        authorizeRepositoryAccess(principal, upload.repositoryId(), upload.branch(), READ_UPLOAD_SCOPE);
        return uploadDetails(upload);
    }

    public CoverageReportDetails getCoverageReport(UUID uploadId, String authorizationHeader) {
        QueuedUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new InvalidUploadException("not_found", "Upload not found"));
        RepositoryApiKeyPrincipal principal = authenticator.authenticateRepositoryAccess(
                authorizationHeader,
                upload.repositoryId(),
                upload.branch());
        authorizeRepositoryAccess(principal, upload.repositoryId(), upload.branch(), READ_UPLOAD_SCOPE);
        return uploadRepository.coverageReportFor(uploadId)
                .orElseThrow(() -> new InvalidUploadException("not_found", "Coverage report not found"));
    }

    public RunnerUploadToken createRunnerUploadToken(String authorizationHeader, UUID repositoryId, String branch) {
        if (runnerTokenIssuer == null) {
            throw new InvalidUploadException("unauthorized", "Runner upload tokens are not configured");
        }
        if (repositoryId == null) {
            throw new InvalidUploadException("validation_error", "repository_id is required");
        }
        if (branch == null || branch.isBlank()) {
            throw new InvalidUploadException("validation_error", "branch is required");
        }
        RepositoryApiKeyPrincipal principal = authenticator.authenticateRepositoryAccess(
                authorizationHeader,
                repositoryId,
                branch);
        authorizeRepositoryAccess(principal, repositoryId, branch, CREATE_UPLOAD_SCOPE);
        return runnerTokenIssuer.issue(principal, repositoryId, branch, RUNNER_UPLOAD_TOKEN_TTL);
    }

    private UploadDetails uploadDetails(QueuedUpload upload) {
        List<ArtifactDetails> artifacts = uploadRepository.artifactsFor(upload.uploadId()).stream()
                .map(artifact -> new ArtifactDetails(
                        artifact.name(),
                        artifact.kind(),
                        artifact.format(),
                        "stored",
                        artifact.sizeBytes()))
                .toList();
        return new UploadDetails(
                upload.uploadId(),
                upload.repositoryId(),
                upload.commitSha(),
                upload.status(),
                upload.analysisJobId().orElse(null),
                artifacts,
                upload.acceptedAt());
    }

    private UploadAccepted acceptNewUpload(CreateUploadCommand command, RepositoryApiKeyPrincipal principal) {
        UUID uploadId = UUID.randomUUID();
        Instant acceptedAt = clock.instant();
        List<StoredArtifact> storedArtifacts = command.artifacts().stream()
                .map(artifact -> artifactStorage.store(principal.tenantId(), uploadId, artifact))
                .toList();

        QueuedUpload uploadWithoutJob = new QueuedUpload(
                uploadId,
                principal.tenantId(),
                command.repositoryId(),
                java.util.Optional.ofNullable(principal.apiKeyId()),
                command.commitSha(),
                command.branch(),
                command.pullRequestNumber(),
                command.ciProvider(),
                command.ciBuildId(),
                command.ciBuildUrl(),
                command.flags(),
                command.ignore(),
                command.component(),
                command.packageName(),
                UploadStatus.QUEUED,
                command.idempotencyKey(),
                acceptedAt,
                java.util.Optional.empty());

        AnalysisJob job = workQueue.enqueueAnalysis(uploadWithoutJob);
        QueuedUpload upload = uploadWithoutJob.withAnalysisJobId(job.jobId());
        try {
            uploadRepository.save(upload, storedArtifacts);
        } catch (DuplicateUploadException exception) {
            return uploadRepository.findByIdempotencyKey(command.repositoryId(), command.idempotencyKey())
                    .map(this::toAccepted)
                    .orElseThrow(() -> exception);
        }
        eventPublisher.publish(new UploadEvent(
                UUID.randomUUID(),
                upload.tenantId(),
                upload.uploadId(),
                "upload.received",
                Map.of(
                        "repository_id", upload.repositoryId().toString(),
                        "commit_sha", upload.commitSha(),
                        "analysis_job_id", job.jobId().toString()),
                acceptedAt));
        return toAccepted(upload);
    }

    private void validate(CreateUploadCommand command) {
        if (command.authorizationHeader() == null || command.authorizationHeader().isBlank()) {
            throw new InvalidUploadException("unauthorized", "Authorization is required");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new InvalidUploadException("validation_error", "Idempotency-Key is required");
        }
        if (command.commitSha() == null || command.commitSha().isBlank()) {
            throw new InvalidUploadException("validation_error", "commit_sha is required");
        }
        if (command.branch() == null || command.branch().isBlank()) {
            throw new InvalidUploadException("validation_error", "branch is required");
        }
        if (command.artifacts().isEmpty()) {
            throw new InvalidUploadException("validation_error", "at least one artifact is required");
        }
        try {
            CoverageIgnoreRules.validate(command.ignore());
        } catch (InvalidCoverageIgnoreRuleException exception) {
            throw new InvalidUploadException("validation_error", exception.getMessage());
        }
        command.artifacts().forEach(this::validateArtifact);
    }

    private CreateUploadCommand resolveRepository(CreateUploadCommand command, RepositoryApiKeyPrincipal principal) {
        if (command.repositoryId() != null) {
            return command;
        }
        return new CreateUploadCommand(
                command.authorizationHeader(),
                command.idempotencyKey(),
                principal.repositoryId(),
                command.commitSha(),
                command.branch(),
                command.pullRequestNumber(),
                command.ciProvider(),
                command.ciBuildId(),
                command.ciBuildUrl(),
                command.flags(),
                command.ignore(),
                command.component(),
                command.packageName(),
                command.artifacts());
    }

    private void validateArtifact(dev.vericov.upload.domain.UploadArtifactInput artifact) {
        if (artifact.name() == null || artifact.name().isBlank()) {
            throw new InvalidUploadException("validation_error", "artifact name is required");
        }
        if (artifact.name().contains("/") || artifact.name().contains("\\") || artifact.name().contains("..")) {
            throw new InvalidUploadException("validation_error", "artifact name must be a file name");
        }
        if (artifact.format() == null || artifact.format().isBlank()) {
            throw new InvalidUploadException("validation_error", "artifact format is required");
        }
        if (artifact.contentType() == null || artifact.contentType().isBlank()) {
            throw new InvalidUploadException("validation_error", "artifact content_type is required");
        }
        if (artifact.content().length == 0) {
            throw new InvalidUploadException("validation_error", "artifact content must not be empty");
        }
    }

    private void authorize(RepositoryApiKeyPrincipal principal, CreateUploadCommand command) {
        authorizeRepositoryAccess(principal, command.repositoryId(), command.branch(), CREATE_UPLOAD_SCOPE);
    }

    private void authorizeRepositoryAccess(
            RepositoryApiKeyPrincipal principal,
            UUID repositoryId,
            String branch,
            String requiredScope) {
        if (!principal.repositoryId().equals(repositoryId)) {
            throw new InvalidUploadException("forbidden", "API key is not valid for this repository");
        }
        if (!principal.hasScope(requiredScope)) {
            throw new InvalidUploadException("forbidden", "API key does not have " + requiredScope + " scope");
        }
        if (!principal.allowsBranch(branch)) {
            throw new InvalidUploadException("forbidden", "API key is not allowed for this branch");
        }
    }

    private UploadAccepted toAccepted(QueuedUpload upload) {
        return new UploadAccepted(
                upload.uploadId(),
                upload.status(),
                "/api/v1/uploads/" + upload.uploadId(),
                upload.repositoryId(),
                upload.commitSha(),
                upload.analysisJobId().orElseThrow());
    }
}
