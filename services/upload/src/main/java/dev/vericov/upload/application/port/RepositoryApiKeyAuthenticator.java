package dev.vericov.upload.application.port;

import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;

public interface RepositoryApiKeyAuthenticator {
    RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command);

    default RepositoryApiKeyPrincipal authenticateRepositoryAccess(
            String authorizationHeader,
            java.util.UUID repositoryId,
            String branch) {
        return authenticate(new CreateUploadCommand(
                authorizationHeader,
                "read-access",
                repositoryId,
                "read-access",
                branch,
                null,
                null,
                null,
                null,
                java.util.List.of(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.List.of(new dev.vericov.upload.domain.UploadArtifactInput(
                        "read-access.txt",
                        dev.vericov.upload.domain.ArtifactKind.METADATA,
                        "text",
                        "text/plain",
                        new byte[] {1}))));
    }
}
