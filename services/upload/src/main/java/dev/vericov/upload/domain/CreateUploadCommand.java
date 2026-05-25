package dev.vericov.upload.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CreateUploadCommand(
        String authorizationHeader,
        String idempotencyKey,
        UUID repositoryId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String ciProvider,
        String ciBuildId,
        String ciBuildUrl,
        List<String> flags,
        Optional<String> component,
        Optional<String> packageName,
        List<UploadArtifactInput> artifacts) {

    public CreateUploadCommand {
        flags = List.copyOf(flags == null ? List.of() : flags);
        component = component == null ? Optional.empty() : component;
        packageName = packageName == null ? Optional.empty() : packageName;
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
    }
}
