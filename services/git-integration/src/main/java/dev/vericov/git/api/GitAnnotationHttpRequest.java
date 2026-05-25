package dev.vericov.git.api;

import dev.vericov.git.application.GitAnnotationInput;
import jakarta.json.bind.annotation.JsonbProperty;

public record GitAnnotationHttpRequest(
        String path,
        @JsonbProperty("start_line")
        int startLine,
        @JsonbProperty("end_line")
        int endLine,
        @JsonbProperty("annotation_level")
        String annotationLevel,
        String message) {

    public GitAnnotationInput toInput() {
        return new GitAnnotationInput(path, startLine, endLine, annotationLevel, message);
    }
}
