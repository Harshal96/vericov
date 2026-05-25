package dev.vericov.upload.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record CreateRunnerUploadTokenHttpRequest(
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String branch) {
}
