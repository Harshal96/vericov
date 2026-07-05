package dev.vericov.upload.config;

import dev.vericov.upload.application.AnalysisJob;
import dev.vericov.upload.application.CoverageQueryService;
import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.DashboardRepository;
import dev.vericov.upload.application.DashboardRepositoryOverview;
import dev.vericov.upload.application.DashboardQueryService;
import dev.vericov.upload.application.InMemoryUploadRepository;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.UploadApplicationService;
import dev.vericov.upload.application.UploadEvent;
import dev.vericov.upload.application.port.DashboardQueryRepository;
import dev.vericov.upload.application.port.ArtifactStorage;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.application.port.TenantAuthenticator;
import dev.vericov.upload.application.port.UploadEventPublisher;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.application.port.UploadWorkQueue;
import dev.vericov.upload.adapter.auth.JdbcRepositoryApiKeyAuthenticator;
import dev.vericov.upload.adapter.auth.GithubActionsOidcVerifier;
import dev.vericov.upload.adapter.auth.HmacRunnerUploadTokenIssuer;
import dev.vericov.upload.adapter.auth.InternalTokenAuthenticator;
import dev.vericov.upload.adapter.auth.RepositoryApiKeySecretHasher;
import dev.vericov.upload.adapter.jdbc.JdbcDashboardQueryRepository;
import dev.vericov.upload.adapter.jdbc.DriverManagerDataSource;
import dev.vericov.upload.adapter.jdbc.JdbcUploadRepository;
import dev.vericov.upload.adapter.storage.HttpSupabaseObjectStorageClient;
import dev.vericov.upload.adapter.storage.FileSystemArtifactStorage;
import dev.vericov.upload.adapter.storage.RawArtifactBucketMapping;
import dev.vericov.upload.adapter.storage.SupabaseStorageArtifactStorage;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import dev.vericov.upload.domain.TenantPrincipal;
import dev.vericov.upload.domain.UploadArtifactInput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

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
                Clock.systemUTC(),
                runnerUploadTokenIssuer());
    }

    @Produces
    @ApplicationScoped
    public CoverageQueryService coverageQueryService(
            RepositoryApiKeyAuthenticator authenticator,
            UploadRepository uploadRepository) {
        return new CoverageQueryService(authenticator, uploadRepository);
    }

    @Produces
    @ApplicationScoped
    public DashboardQueryService dashboardQueryService(
            TenantAuthenticator authenticator,
            DashboardQueryRepository dashboardQueryRepository) {
        return new DashboardQueryService(authenticator, dashboardQueryRepository);
    }

    @Produces
    @ApplicationScoped
    public UploadRepository uploadRepository() {
        String jdbcUrl = env("VERICOV_UPLOAD_DB_URL", env("SUPABASE_DB_URL", ""));
        if (!jdbcUrl.isBlank()) {
            return new JdbcUploadRepository(dataSource(jdbcUrl));
        }
        return new InMemoryUploadRepository();
    }

    @Produces
    @ApplicationScoped
    public DashboardQueryRepository dashboardQueryRepository() {
        String jdbcUrl = env("VERICOV_UPLOAD_DB_URL", env("SUPABASE_DB_URL", ""));
        if (!jdbcUrl.isBlank()) {
            return new JdbcDashboardQueryRepository(dataSource(jdbcUrl));
        }
        return new EmptyDashboardQueryRepository();
    }

    @Produces
    @ApplicationScoped
    public RepositoryApiKeyAuthenticator repositoryApiKeyAuthenticator() {
        String jdbcUrl = env("VERICOV_UPLOAD_DB_URL", env("SUPABASE_DB_URL", ""));
        if (!jdbcUrl.isBlank() && !Boolean.parseBoolean(env("VERICOV_DEV_AUTH_BYPASS", "false"))) {
            return new JdbcRepositoryApiKeyAuthenticator(
                    dataSource(jdbcUrl),
                    new RepositoryApiKeySecretHasher(requiredEnv("VERICOV_REPO_API_KEY_PEPPER")),
                    Clock.systemUTC(),
                    env("VERICOV_RUNNER_JWT_SECRET", ""),
                    env("VERICOV_RUNNER_JWT_ISSUER", "vericov-upload"),
                    env("VERICOV_RUNNER_JWT_AUDIENCE", "vericov-runner-upload"),
                    new GithubActionsOidcVerifier(
                            URI.create(env(
                                    "VERICOV_GITHUB_ACTIONS_OIDC_JWKS_URL",
                                    "https://token.actions.githubusercontent.com/.well-known/jwks")),
                            Clock.systemUTC()));
        }
        return new EnvironmentRepositoryApiKeyAuthenticator();
    }

    @Produces
    @ApplicationScoped
    public TenantAuthenticator tenantAuthenticator() {
        String jdbcUrl = env("VERICOV_UPLOAD_DB_URL", env("SUPABASE_DB_URL", ""));
        if (!jdbcUrl.isBlank() && !Boolean.parseBoolean(env("VERICOV_DEV_AUTH_BYPASS", "false"))) {
            return new InternalTokenAuthenticator(
                    dataSource(jdbcUrl),
                    requiredEnv("VERICOV_INTERNAL_TOKEN_SECRET"),
                    env("VERICOV_INTERNAL_TOKEN_ISSUER", "veriapi"),
                    env("VERICOV_INTERNAL_TOKEN_AUDIENCE", "veri-internal"),
                    Clock.systemUTC());
        }
        return new EnvironmentTenantAuthenticator();
    }

    @Produces
    @ApplicationScoped
    public ArtifactStorage artifactStorage() {
        String backend = artifactStorageBackend();
        if ("supabase".equalsIgnoreCase(backend)) {
            return new SupabaseStorageArtifactStorage(
                    new HttpSupabaseObjectStorageClient(
                            supabaseStorageBaseUri(),
                            requiredEnv("SUPABASE_SERVICE_ROLE_KEY")),
                    new RawArtifactBucketMapping(
                            env("VERICOV_COVERAGE_BUCKET", "coverage-raw"),
                            env("VERICOV_TEST_RESULTS_BUCKET", "test-results-raw"),
                            env("VERICOV_METADATA_BUCKET", "metadata-raw")));
        }
        if ("memory".equalsIgnoreCase(backend)) {
            return new InMemoryArtifactStorage();
        }
        if ("filesystem".equalsIgnoreCase(backend)) {
            return new FileSystemArtifactStorage(
                    Path.of(env("VERICOV_ARTIFACT_STORAGE_ROOT", "/var/lib/vericov/artifacts")),
                    new RawArtifactBucketMapping(
                            env("VERICOV_COVERAGE_BUCKET", "coverage-raw"),
                            env("VERICOV_TEST_RESULTS_BUCKET", "test-results-raw"),
                            env("VERICOV_METADATA_BUCKET", "metadata-raw")));
        }
        throw new IllegalStateException("Unsupported VERICOV_ARTIFACT_STORAGE_BACKEND: " + backend);
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

    private static HmacRunnerUploadTokenIssuer runnerUploadTokenIssuer() {
        String secret = env("VERICOV_RUNNER_JWT_SECRET", "");
        if (secret.isBlank()) {
            return null;
        }
        return new HmacRunnerUploadTokenIssuer(
                secret,
                env("VERICOV_RUNNER_JWT_ISSUER", "vericov-upload"),
                env("VERICOV_RUNNER_JWT_AUDIENCE", "vericov-runner-upload"),
                Clock.systemUTC());
    }

    private static final class EnvironmentRepositoryApiKeyAuthenticator implements RepositoryApiKeyAuthenticator {
        private final String configuredKey = System.getenv("VERICOV_DEV_API_KEY");
        private final UUID tenantId = uuidEnv("VERICOV_DEV_TENANT_ID", "00000000-0000-0000-0000-000000000001");
        private final UUID repositoryId = uuidEnv(
                "VERICOV_DEV_REPOSITORY_ID",
                "00000000-0000-0000-0000-000000000003");
        private final UUID apiKeyId = optionalUuidEnv("VERICOV_DEV_API_KEY_ID");

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
                    repositoryId,
                    apiKeyId,
                    Set.of("uploads:create", "uploads:read"),
                    Set.of("*"));
        }

        private static UUID uuidEnv(String name, String fallback) {
            String value = System.getenv(name);
            return UUID.fromString(value == null || value.isBlank() ? fallback : value);
        }

        private static UUID optionalUuidEnv(String name) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        }
    }

    private static final class EnvironmentTenantAuthenticator implements TenantAuthenticator {
        private final boolean authBypass = Boolean.parseBoolean(env("VERICOV_DEV_AUTH_BYPASS", "false"));
        private final UUID tenantId = uuidEnv("VERICOV_DEV_TENANT_ID", "00000000-0000-0000-0000-000000000001");
        private final UUID externalOrgId = uuidEnv("VERI_DEV_ORG_ID", "00000000-0000-0000-0000-000000000010");

        @Override
        public TenantPrincipal authenticateTenant(String authorizationHeader) {
            if (!authBypass) {
                throw new InvalidUploadException("unauthorized", "Dashboard authentication is not configured");
            }
            return new TenantPrincipal(
                    tenantId,
                    externalOrgId,
                    "dev-user",
                    Set.of("admin"),
                    Set.of("cov:read"));
        }

        private static UUID uuidEnv(String name, String fallback) {
            String value = System.getenv(name);
            return UUID.fromString(value == null || value.isBlank() ? fallback : value);
        }
    }

    private static final class EmptyDashboardQueryRepository implements DashboardQueryRepository {
        @Override
        public DashboardOverview overview(UUID tenantId) {
            return new DashboardOverview(0, 0, null, 0, 0, 0, 0);
        }

        @Override
        public List<DashboardRepositoryOverview> repositories(UUID tenantId) {
            return List.of();
        }

        @Override
        public Map<UUID, List<BigDecimal>> sparklines(UUID tenantId, int perRepository) {
            return Map.of();
        }

        @Override
        public Optional<DashboardRepository> repository(UUID tenantId, UUID repositoryId) {
            return Optional.empty();
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

    private static DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(
                jdbcUrl,
                requiredDbEnv("VERICOV_UPLOAD_DB_USER", "SUPABASE_DB_USER"),
                requiredDbEnv("VERICOV_UPLOAD_DB_PASSWORD", "SUPABASE_DB_PASSWORD"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static String requiredDbEnv(String primaryName, String fallbackName) {
        String primary = System.getenv(primaryName);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return requiredEnv(fallbackName);
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
