package dev.vericov.git.application;

public record GitDiffLineDetails(
        Integer baseLineNumber,
        Integer headLineNumber,
        String changeType) {

    public GitDiffLineDetails {
        if (baseLineNumber != null && baseLineNumber < 1) {
            throw new GitIntegrationException("validation_error", "base_line_number must be positive");
        }
        if (headLineNumber != null && headLineNumber < 1) {
            throw new GitIntegrationException("validation_error", "head_line_number must be positive");
        }
        changeType = GitValues.requireCanonical(changeType, "change_type is required");
        if (!"added".equals(changeType) && !"deleted".equals(changeType) && !"context".equals(changeType)) {
            throw new GitIntegrationException("validation_error", "change_type is invalid");
        }
    }
}
