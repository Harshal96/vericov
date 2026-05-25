package dev.vericov.upload.api;

import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.UploadArtifactInput;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Base64;

public record UploadArtifactHttpRequest(
        String name,
        String kind,
        String format,
        @JsonbProperty("content_type")
        String contentType,
        @JsonbProperty("content_base64")
        String contentBase64) {

    public UploadArtifactInput toDomain() {
        return new UploadArtifactInput(
                name,
                ArtifactKind.fromWireValue(kind),
                format,
                contentType,
                Base64.getDecoder().decode(contentBase64));
    }
}
