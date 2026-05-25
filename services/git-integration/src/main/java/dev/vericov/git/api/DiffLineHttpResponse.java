package dev.vericov.git.api;

import dev.vericov.git.application.GitDiffLineDetails;
import jakarta.json.bind.annotation.JsonbProperty;

public record DiffLineHttpResponse(
        @JsonbProperty("base_line_number")
        Integer baseLineNumber,
        @JsonbProperty("head_line_number")
        Integer headLineNumber,
        @JsonbProperty("change_type")
        String changeType) {

    public static DiffLineHttpResponse from(GitDiffLineDetails details) {
        return new DiffLineHttpResponse(
                details.baseLineNumber(),
                details.headLineNumber(),
                details.changeType());
    }
}
