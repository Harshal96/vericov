package dev.vericov.upload.domain;

public enum UploadStatus {
    ACCEPTED("accepted"),
    QUEUED("queued"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String wireValue;

    UploadStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
