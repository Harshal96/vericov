package dev.vericov.upload.application.port;

import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;

public interface RepositoryApiKeyAuthenticator {
    RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command);
}
