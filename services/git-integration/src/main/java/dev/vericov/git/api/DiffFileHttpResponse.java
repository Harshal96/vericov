package dev.vericov.git.api;

import dev.vericov.git.application.GitDiffFileDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record DiffFileHttpResponse(
        @JsonbProperty("file_path")
        String filePath,
        @JsonbProperty("old_file_path")
        String oldFilePath,
        @JsonbProperty("change_status")
        String changeStatus,
        List<DiffLineHttpResponse> lines) {

    public static DiffFileHttpResponse from(GitDiffFileDetails details) {
        return new DiffFileHttpResponse(
                details.filePath(),
                details.oldFilePath(),
                details.changeStatus(),
                details.lines().stream().map(DiffLineHttpResponse::from).toList());
    }
}
