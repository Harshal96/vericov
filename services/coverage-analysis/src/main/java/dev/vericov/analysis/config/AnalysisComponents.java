package dev.vericov.analysis.config;

import dev.vericov.analysis.adapter.git.InternalGitDiffHttpClient;
import dev.vericov.analysis.adapter.controlplane.InternalControlPlaneRepositoryContextClient;
import dev.vericov.analysis.adapter.jdbc.AnalysisMessageJsonCodec;
import dev.vericov.analysis.adapter.jdbc.DriverManagerDataSource;
import dev.vericov.analysis.adapter.jdbc.JdbcAnalysisJobQueue;
import dev.vericov.analysis.adapter.jdbc.JdbcAnalysisJobRepository;
import dev.vericov.analysis.adapter.jdbc.JdbcCoverageAnalysisInputRepository;
import dev.vericov.analysis.adapter.jdbc.JdbcCoverageReportRepository;
import dev.vericov.analysis.adapter.jdbc.JdbcGateConfigurationRepository;
import dev.vericov.analysis.adapter.jdbc.JdbcPrDiffCoverageRepository;
import dev.vericov.analysis.adapter.jdbc.JdbcTestRunRepository;
import dev.vericov.analysis.adapter.storage.HttpSupabaseArtifactContentStore;
import dev.vericov.analysis.adapter.storage.SupabaseNormalizedCoverageStore;
import dev.vericov.analysis.application.AnalysisMessageHandler;
import dev.vericov.analysis.application.AnalysisWorker;
import dev.vericov.analysis.application.DefaultCoverageAnalysisProcessor;
import dev.vericov.analysis.application.DefaultPrDiffCoverageProcessor;
import dev.vericov.analysis.application.PrDiffCoverageProcessor;
import dev.vericov.analysis.application.UploadAnalysisEventHandler;
import dev.vericov.analysis.application.port.AnalysisJobQueue;
import dev.vericov.analysis.application.port.AnalysisJobRepository;
import dev.vericov.analysis.application.port.CoverageAnalysisProcessor;
import dev.vericov.analysis.application.port.RepositoryContextRepository;
import dev.vericov.analysis.coverage.CloverCoverageParser;
import dev.vericov.analysis.coverage.CoberturaCoverageParser;
import dev.vericov.analysis.coverage.CoverageParserRegistry;
import dev.vericov.analysis.coverage.GcovCoverageParser;
import dev.vericov.analysis.coverage.GoCoverProfileParser;
import dev.vericov.analysis.coverage.JacocoCoverageParser;
import dev.vericov.analysis.coverage.LcovCoverageParser;
import dev.vericov.analysis.coverage.NormalizedCoverageMapSerializer;
import dev.vericov.analysis.coverage.SecureXmlCoverageDocumentReader;
import dev.vericov.analysis.domain.AnalysisFailureDecision;
import dev.vericov.analysis.domain.AnalysisJobStartResult;
import dev.vericov.analysis.domain.QueuedAnalysisMessage;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import dev.vericov.analysis.testresults.JUnitTestResultParser;
import dev.vericov.analysis.testresults.SecureXmlTestResultDocumentReader;
import dev.vericov.analysis.testresults.TestResultParserRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

@ApplicationScoped
public class AnalysisComponents {

    @Produces
    @ApplicationScoped
    public AnalysisMessageJsonCodec analysisMessageJsonCodec() {
        return new AnalysisMessageJsonCodec();
    }

    @Produces
    @ApplicationScoped
    public AnalysisJobQueue analysisJobQueue(AnalysisMessageJsonCodec codec) {
        String jdbcUrl = System.getenv("VERICOV_ANALYSIS_DB_URL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new InMemoryAnalysisJobQueue();
        }
        return new JdbcAnalysisJobQueue(dataSource(jdbcUrl), codec);
    }

    @Produces
    @ApplicationScoped
    public AnalysisJobRepository analysisJobRepository() {
        String jdbcUrl = System.getenv("VERICOV_ANALYSIS_DB_URL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new InMemoryAnalysisJobRepository();
        }
        return new JdbcAnalysisJobRepository(dataSource(jdbcUrl), visibilityTimeoutSeconds());
    }

    @Produces
    @ApplicationScoped
    public RepositoryContextRepository repositoryContextRepository() {
        String token = System.getenv("VERICOV_INTERNAL_SERVICE_TOKEN");
        if (token != null && !token.isBlank()) {
            return new InternalControlPlaneRepositoryContextClient(
                    URI.create(env("VERICOV_CONTROL_PLANE_BASE_URL", "http://127.0.0.1:8081")),
                    token);
        }
        return (tenantId, repositoryId, commitSha, branch, pr) -> new dev.vericov.analysis.gates.RepositoryContext(
                "ctx-" + Instant.now().toString(),
                List.of(),
                List.of(),
                Map.of());
    }

    @Produces
    @ApplicationScoped
    public CoverageAnalysisProcessor coverageAnalysisProcessor(RepositoryContextRepository repositoryContextRepository) {
        String jdbcUrl = System.getenv("VERICOV_ANALYSIS_DB_URL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new PlaceholderCoverageAnalysisProcessor();
        }
        DataSource dataSource = dataSource(jdbcUrl);
        SecureXmlCoverageDocumentReader xmlReader = new SecureXmlCoverageDocumentReader();
        JdbcCoverageReportRepository reportRepository = new JdbcCoverageReportRepository(dataSource);
        URI storageBaseUri = supabaseStorageBaseUri();
        String serviceRoleKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
        return new DefaultCoverageAnalysisProcessor(
                new JdbcCoverageAnalysisInputRepository(dataSource),
                new HttpSupabaseArtifactContentStore(storageBaseUri, serviceRoleKey),
                reportRepository,
                new JdbcGateConfigurationRepository(dataSource),
                repositoryContextRepository,
                new SupabaseNormalizedCoverageStore(
                        storageBaseUri,
                        serviceRoleKey,
                        normalizedCoverageBucket(),
                        new NormalizedCoverageMapSerializer()),
                new CoverageParserRegistry(List.of(
                        new LcovCoverageParser(),
                        new CoberturaCoverageParser(xmlReader),
                        new JacocoCoverageParser(xmlReader),
                        new CloverCoverageParser(xmlReader),
                        new GoCoverProfileParser(),
                        new GcovCoverageParser())),
                new TestResultParserRegistry(List.of(
                        new JUnitTestResultParser(new SecureXmlTestResultDocumentReader()))),
                new JdbcTestRunRepository(dataSource),
                prDiffCoverageProcessor(dataSource, reportRepository),
                Clock.systemUTC());
    }

    @Produces
    @ApplicationScoped
    public AnalysisMessageHandler analysisMessageHandler(
            AnalysisJobQueue queue,
            AnalysisJobRepository repository,
            CoverageAnalysisProcessor processor) {
        return new UploadAnalysisEventHandler(
                queue,
                repository,
                processor,
                workerId(),
                Clock.systemUTC(),
                queueName(),
                deadLetterQueueName());
    }

    @Produces
    @ApplicationScoped
    public AnalysisWorker analysisWorker(AnalysisJobQueue queue, AnalysisMessageHandler handler) {
        return new AnalysisWorker(
                queue,
                handler,
                queueName(),
                visibilityTimeoutSeconds(),
                intEnv("VERICOV_ANALYSIS_BATCH_SIZE", 10));
    }

    private static DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(
                jdbcUrl,
                requiredEnv("VERICOV_ANALYSIS_DB_USER"),
                requiredEnv("VERICOV_ANALYSIS_DB_PASSWORD"));
    }

    private static String queueName() {
        return env("VERICOV_ANALYSIS_QUEUE_NAME", UploadAnalysisEventHandler.DEFAULT_QUEUE_NAME);
    }

    private static String deadLetterQueueName() {
        return env("VERICOV_ANALYSIS_DEAD_LETTER_QUEUE_NAME", UploadAnalysisEventHandler.DEFAULT_DEAD_LETTER_QUEUE_NAME);
    }

    private static String workerId() {
        return env("VERICOV_ANALYSIS_WORKER_ID", "coverage-analysis-local");
    }

    private static int visibilityTimeoutSeconds() {
        return intEnv("VERICOV_ANALYSIS_VISIBILITY_TIMEOUT_SECONDS", 300);
    }

    private static String normalizedCoverageBucket() {
        return env("VERICOV_NORMALIZED_COVERAGE_BUCKET", "coverage-normalized");
    }

    private static URI supabaseStorageBaseUri() {
        String storageUrl = env("SUPABASE_STORAGE_URL", "");
        if (!storageUrl.isBlank()) {
            return URI.create(storageUrl);
        }
        String supabaseUrl = requiredEnv("SUPABASE_URL");
        return URI.create(trimTrailingSlash(supabaseUrl) + "/storage/v1");
    }

    private static PrDiffCoverageProcessor prDiffCoverageProcessor(
            DataSource dataSource,
            JdbcCoverageReportRepository reportRepository) {
        String token = env("VERICOV_INTERNAL_SERVICE_TOKEN", "");
        if (token.isBlank()) {
            return PrDiffCoverageProcessor.noop();
        }
        return new DefaultPrDiffCoverageProcessor(
                new InternalGitDiffHttpClient(
                        URI.create(env("VERICOV_GIT_BASE_URL", "http://127.0.0.1:8083")),
                        token),
                reportRepository,
                new JdbcPrDiffCoverageRepository(dataSource));
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
            throw new IllegalStateException(name + " is required when VERICOV_ANALYSIS_DB_URL is set");
        }
        return value;
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static final class PlaceholderCoverageAnalysisProcessor implements CoverageAnalysisProcessor {
        @Override
        public void process(UploadReceivedEvent event) {
            throw new UnsupportedOperationException(
                    "Coverage artifact parsing is not implemented yet for analysis job " + event.analysisJobId());
        }
    }

    private static final class InMemoryAnalysisJobQueue implements AnalysisJobQueue {
        @Override
        public List<QueuedAnalysisMessage> read(String queueName, int visibilityTimeoutSeconds, int batchSize) {
            return List.of();
        }

        @Override
        public void archive(String queueName, long messageId) {
        }

        @Override
        public void reschedule(String queueName, long messageId, int delaySeconds) {
        }

        @Override
        public void moveToDeadLetter(
                String sourceQueueName,
                String deadLetterQueueName,
                QueuedAnalysisMessage message,
                String reason) {
        }
    }

    private static final class InMemoryAnalysisJobRepository implements AnalysisJobRepository {
        private final Map<UUID, Integer> attemptsByJobId = new ConcurrentHashMap<>();

        @Override
        public AnalysisJobStartResult startJob(UUID jobId, String workerId, Instant startedAt) {
            attemptsByJobId.merge(jobId, 1, Integer::sum);
            return AnalysisJobStartResult.started();
        }

        @Override
        public void completeJob(UUID jobId, Instant finishedAt) {
        }

        @Override
        public AnalysisFailureDecision recordFailure(UUID jobId, String workerId, Instant failedAt, String errorMessage) {
            int attempts = attemptsByJobId.getOrDefault(jobId, 1);
            return attempts >= 5 ? AnalysisFailureDecision.deadLetter() : AnalysisFailureDecision.retryLater();
        }
    }
}
