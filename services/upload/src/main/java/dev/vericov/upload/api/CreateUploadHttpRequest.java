package dev.vericov.upload.api;

import dev.vericov.upload.domain.CreateUploadCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CreateUploadHttpRequest(
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("commit_sha")
        String commitSha,
        String branch,
        @JsonbProperty("pull_request_number")
        Integer pullRequestNumber,
        @JsonbProperty("ci_provider")
        String ciProvider,
        @JsonbProperty("ci_build_id")
        String ciBuildId,
        @JsonbProperty("ci_build_url")
        String ciBuildUrl,
        List<String> flags,
        String component,
        @JsonbProperty("package")
        String packageName,
        List<UploadArtifactHttpRequest> artifacts) {

    public CreateUploadHttpRequest {
        flags = List.copyOf(flags == null ? List.of() : flags);
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
    }

    public CreateUploadCommand toCommand(String authorizationHeader, String idempotencyKey) {
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
                Optional.ofNullable(blankToNull(component)),
                Optional.ofNullable(blankToNull(packageName)),
                artifacts.stream().map(UploadArtifactHttpRequest::toDomain).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
