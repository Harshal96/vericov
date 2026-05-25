package dev.vericov.upload.domain;

import java.util.Arrays;

public record UploadArtifactInput(
        String name,
        ArtifactKind kind,
        String format,
        String contentType,
        byte[] content) {

    public UploadArtifactInput {
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
