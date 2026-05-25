package dev.vericov.git.application;

import java.util.Set;

public record GitAnnotationInput(
        String path,
        int startLine,
        int endLine,
        String annotationLevel,
        String message) {
    private static final Set<String> LEVELS = Set.of("notice", "warning", "failure");

    public GitAnnotationInput {
        path = GitValues.requireTrimmed(path, "path is required");
        if (startLine < 1) {
            throw new GitIntegrationException("validation_error", "start_line must be positive");
        }
        if (endLine < startLine) {
            throw new GitIntegrationException("validation_error", "end_line must be greater than or equal to start_line");
        }
        annotationLevel = GitValues.requireCanonical(annotationLevel, "annotation_level is required");
        if (!LEVELS.contains(annotationLevel)) {
            throw new GitIntegrationException("validation_error", "annotation_level is invalid");
        }
        message = GitValues.requireTrimmed(message, "message is required");
    }
}
