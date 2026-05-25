package dev.vericov.upload.adapter.storage;

import dev.vericov.upload.domain.ArtifactKind;
import java.util.Objects;

public record RawArtifactBucketMapping(
        String coverageBucket,
        String testResultsBucket,
        String metadataBucket) {

    public RawArtifactBucketMapping {
        coverageBucket = requireBucket(coverageBucket, "coverageBucket");
        testResultsBucket = requireBucket(testResultsBucket, "testResultsBucket");
        metadataBucket = requireBucket(metadataBucket, "metadataBucket");
    }

    public String bucketFor(ArtifactKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case COVERAGE -> coverageBucket;
            case TEST_RESULTS -> testResultsBucket;
            case METADATA -> metadataBucket;
        };
    }

    private static String requireBucket(String bucket, String fieldName) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return bucket;
    }
}
