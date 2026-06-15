package dev.vericov.upload.domain;

import dev.vericov.componentconfig.ComponentDefinition;
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
        List<String> ignore,
        List<ComponentDefinition> components,
        String configSha256,
        boolean configSnapshotPresent,
        Optional<String> component,
        Optional<String> packageName,
        List<UploadArtifactInput> artifacts) {

    public CreateUploadCommand {
        flags = List.copyOf(flags == null ? List.of() : flags);
        ignore = List.copyOf(ignore == null ? List.of() : ignore);
        components = List.copyOf(components == null ? List.of() : components);
        component = component == null ? Optional.empty() : component;
        packageName = packageName == null ? Optional.empty() : packageName;
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
    }

    public CreateUploadCommand(
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
            List<String> ignore,
            Optional<String> component,
            Optional<String> packageName,
            List<UploadArtifactInput> artifacts) {
        this(
                authorizationHeader,
                idempotencyKey,
                repositoryId,
                commitSha,
                branch,
                pullRequestNumber,
                ciProvider,
                ciBuildId,
                ciBuildUrl,
                flags,
                ignore,
                List.of(),
                null,
                ignore != null,
                component,
                packageName,
                artifacts);
    }

    public CreateUploadCommand(
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
        this(
                authorizationHeader,
                idempotencyKey,
                repositoryId,
                commitSha,
                branch,
                pullRequestNumber,
                ciProvider,
                ciBuildId,
                ciBuildUrl,
                flags,
                List.of(),
                List.of(),
                null,
                false,
                component,
                packageName,
                artifacts);
    }

    public CreateUploadCommand withConfig(
            List<ComponentDefinition> components,
            String configSha256,
            boolean configSnapshotPresent) {
        return new CreateUploadCommand(
                authorizationHeader,
                idempotencyKey,
                repositoryId,
                commitSha,
                branch,
                pullRequestNumber,
                ciProvider,
                ciBuildId,
                ciBuildUrl,
                flags,
                ignore,
                components,
                configSha256,
                configSnapshotPresent,
                component,
                packageName,
                artifacts);
    }
}
