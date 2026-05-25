package dev.vericov.upload.application.port;

import dev.vericov.upload.application.RunnerUploadToken;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import java.time.Duration;
import java.util.UUID;

public interface RunnerUploadTokenIssuer {
    RunnerUploadToken issue(RepositoryApiKeyPrincipal principal, UUID repositoryId, String branch, Duration ttl);
}
