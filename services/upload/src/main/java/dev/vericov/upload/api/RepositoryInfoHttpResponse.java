package dev.vericov.upload.api;

import dev.vericov.upload.application.RepositoryInfo;
import jakarta.json.bind.annotation.JsonbProperty;

public record RepositoryInfoHttpResponse(
        @JsonbProperty("full_name") String fullName,
        @JsonbProperty("default_branch") String defaultBranch) {

    public static RepositoryInfoHttpResponse from(RepositoryInfo info) {
        return new RepositoryInfoHttpResponse(info.fullName(), info.defaultBranch());
    }
}
