package dev.vericov.upload.config;

import dev.vericov.upload.application.AnalysisJob;
import dev.vericov.upload.application.InMemoryUploadRepository;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.UploadApplicationService;
import dev.vericov.upload.application.UploadEvent;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.adapter.storage.HttpSupabaseObjectStorageClient;
import dev.vericov.upload.adapter.storage.RawArtifactBucketMapping;
import dev.vericov.upload.adapter.storage.SupabaseStorageArtifactStorage;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.UploadArtifactInput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DevelopmentUploadComponents {
    @Produces
    @ApplicationScoped
    public UploadApplicationService uploadApplicationService(
            RepositoryApiKeyAuthenticator authenticator,
            UploadRepository uploadRepository,
            ArtifactStorage artifactStorage,
            UploadEventPublisher eventPublisher,
            UploadWorkQueue workQueue) {
        return new UploadApplicationService(
                authenticator,
                uploadRepository,
                artifactStorage,
                eventPublisher,
                workQueue,
                Clock.systemUTC());
    }

    @Produces
    @ApplicationScoped
    public UploadRepository uploadRepository() {
        return new InMemoryUploadRepository();
    }

    @Produces
    @ApplicationScoped
    public RepositoryApiKeyAuthenticator repositoryApiKeyAuthenticator() {
        return new EnvironmentRepositoryApiKeyAuthenticator();
    }

    @Produces
    @ApplicationScoped
    public ArtifactStorage artifactStorage() {
        if ("supabase".equalsIgnoreCase(artifactStorageBackend())) {
            return new SupabaseStorageArtifactStorage(
                    new HttpSupabaseObjectStorageClient(
                            supabaseStorageBaseUri(),
                            requiredEnv("SUPABASE_SERVICE_ROLE_KEY")),
                    new RawArtifactBucketMapping(
                            env("VERICOV_COVERAGE_BUCKET", "coverage-raw"),
                            env("VERICOV_TEST_RESULTS_BUCKET", "test-results-raw"),
                            env("VERICOV_METADATA_BUCKET", "metadata-raw")));
        }
        return new InMemoryArtifactStorage();
    }

    @Produces
    @ApplicationScoped
    public UploadEventPublisher uploadEventPublisher() {
        return new InMemoryUploadEventPublisher();
    }

    @Produces
    @ApplicationScoped
    public UploadWorkQueue uploadWorkQueue() {
        return new InMemoryUploadWorkQueue();
    }

    private static final class EnvironmentRepositoryApiKeyAuthenticator implements RepositoryApiKeyAuthenticator {
        private final String configuredKey = System.getenv("VERICOV_DEV_API_KEY");
        private final UUID tenantId = uuidEnv("VERICOV_DEV_TENANT_ID", "00000000-0000-0000-0000-000000000001");
        private final UUID apiKeyId = uuidEnv("VERICOV_DEV_API_KEY_ID", "00000000-0000-0000-0000-000000000002");

        @Override
        public RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command) {
            if (configuredKey == null || configuredKey.isBlank()) {
                throw new InvalidUploadException("unauthorized", "VERICOV_DEV_API_KEY is required for local uploads");
            }
            String presentedKey = command.authorizationHeader().replaceFirst("(?i)^Bearer\\s+", "").trim();
            if (!MessageDigest.isEqual(configuredKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    presentedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                throw new InvalidUploadException("unauthorized", "Invalid API key");
            }
            return new RepositoryApiKeyPrincipal(
                    tenantId,
                    command.repositoryId(),
                    apiKeyId,
                    Set.of("uploads:create", "uploads:read"),
                    Set.of("*"));
        }

        private static UUID uuidEnv(String name, String fallback) {
            String value = System.getenv(name);
            return UUID.fromString(value == null || value.isBlank() ? fallback : value);
        }
    }

    private static String artifactStorageBackend() {
        return env("VERICOV_ARTIFACT_STORAGE_BACKEND", supabaseStorageConfigured() ? "supabase" : "memory");
    }

    private static boolean supabaseStorageConfigured() {
        return !env("SUPABASE_SERVICE_ROLE_KEY", "").isBlank()
                && (!env("SUPABASE_STORAGE_URL", "").isBlank() || !env("SUPABASE_URL", "").isBlank());
    }

    private static URI supabaseStorageBaseUri() {
        String storageUrl = env("SUPABASE_STORAGE_URL", "");
        if (!storageUrl.isBlank()) {
            return URI.create(storageUrl);
        }
        String supabaseUrl = requiredEnv("SUPABASE_URL");
        return URI.create(trimTrailingSlash(supabaseUrl) + "/storage/v1");
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static final class InMemoryArtifactStorage implements ArtifactStorage {
        private final Map<String, byte[]> contentByPath = new ConcurrentHashMap<>();

        @Override
        public StoredArtifact store(UUID tenantId, UUID uploadId, UploadArtifactInput artifact) {
            String storageBucket = artifact.kind().wireValue() + "-raw";
            String storagePath = tenantId + "/" + uploadId + "/" + artifact.name();
            byte[] content = artifact.content();
            contentByPath.put(storagePath, content);
            return new StoredArtifact(
                    artifact.name(),
                    artifact.kind(),
                    artifact.format(),
                    artifact.contentType(),
                    content.length,
                    storageBucket,
                    storagePath,
                    sha256(content));
        }

        private static String sha256(byte[] content) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(content));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available", exception);
            }
        }
    }

    private static final class InMemoryUploadEventPublisher implements UploadEventPublisher {
        private final Map<UUID, UploadEvent> events = new ConcurrentHashMap<>();

        @Override
        public void publish(UploadEvent event) {
            events.put(event.eventId(), event);
        }
    }

    private static final class InMemoryUploadWorkQueue implements UploadWorkQueue {
        private final Map<UUID, AnalysisJob> jobs = new ConcurrentHashMap<>();

        @Override
        public AnalysisJob enqueueAnalysis(QueuedUpload upload) {
            AnalysisJob job = new AnalysisJob(
                    UUID.randomUUID(),
                    upload.uploadId(),
                    upload.repositoryId(),
                    upload.commitSha());
            jobs.put(job.jobId(), job);
            return job;
        }
    }
}
