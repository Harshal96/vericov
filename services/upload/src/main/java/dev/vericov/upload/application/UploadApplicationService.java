package dev.vericov.upload.application;

import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.UploadStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UploadApplicationService {
    private static final String CREATE_UPLOAD_SCOPE = "uploads:create";

    private final RepositoryApiKeyAuthenticator authenticator;
    private final UploadRepository uploadRepository;
    private final ArtifactStorage artifactStorage;
    private final UploadEventPublisher eventPublisher;
    private final UploadWorkQueue workQueue;
    private final Clock clock;

    public UploadApplicationService(
            RepositoryApiKeyAuthenticator authenticator,
            UploadRepository uploadRepository,
            ArtifactStorage artifactStorage,
            UploadEventPublisher eventPublisher,
            UploadWorkQueue workQueue,
            Clock clock) {
        this.authenticator = authenticator;
        this.uploadRepository = uploadRepository;
        this.artifactStorage = artifactStorage;
        this.eventPublisher = eventPublisher;
        this.workQueue = workQueue;
        this.clock = clock;
    }

    public UploadAccepted acceptUpload(CreateUploadCommand command) {
        validate(command);
        RepositoryApiKeyPrincipal principal = authenticator.authenticate(command);
        authorize(principal, command);

        return uploadRepository.findByIdempotencyKey(command.repositoryId(), command.idempotencyKey())
                .map(this::toAccepted)
                .orElseGet(() -> acceptNewUpload(command, principal));
    }

    public UploadDetails getUpload(UUID uploadId) {
        QueuedUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new InvalidUploadException("not_found", "Upload not found"));
        List<ArtifactDetails> artifacts = uploadRepository.artifactsFor(uploadId).stream()
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
                java.util.Optional.of(principal.apiKeyId()),
                command.commitSha(),
                command.branch(),
                command.pullRequestNumber(),
                command.ciProvider(),
                command.ciBuildId(),
                command.ciBuildUrl(),
                command.flags(),
                command.component(),
                command.packageName(),
                UploadStatus.QUEUED,
                command.idempotencyKey(),
                acceptedAt,
                java.util.Optional.empty());

        AnalysisJob job = workQueue.enqueueAnalysis(uploadWithoutJob);
        QueuedUpload upload = uploadWithoutJob.withAnalysisJobId(job.jobId());
        uploadRepository.save(upload, storedArtifacts);
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
        if (command.repositoryId() == null) {
            throw new InvalidUploadException("validation_error", "repository_id is required");
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
        command.artifacts().forEach(this::validateArtifact);
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
        if (!principal.repositoryId().equals(command.repositoryId())) {
            throw new InvalidUploadException("forbidden", "API key is not valid for this repository");
        }
        if (!principal.hasScope(CREATE_UPLOAD_SCOPE)) {
            throw new InvalidUploadException("forbidden", "API key does not have uploads:create scope");
        }
        if (!principal.allowsBranch(command.branch())) {
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
