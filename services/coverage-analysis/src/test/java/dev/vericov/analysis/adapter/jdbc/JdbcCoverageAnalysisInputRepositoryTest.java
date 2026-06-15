package dev.vericov.analysis.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import dev.vericov.analysis.domain.NonRetryableAnalysisException;
import org.junit.jupiter.api.Test;

class JdbcCoverageAnalysisInputRepositoryTest {
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID ARTIFACT_ID = UUID.fromString("52d0e554-4ce9-418c-96af-2c1c4cf17e3c");

    @Test
    void loadsUploadMetadataAndArtifactsIncludingNullPullRequestNumber() {
        JdbcProxySupport.RecordingDataSource dataSource = JdbcProxySupport.dataSource()
                .whenSqlContains(
                        "from vericov.uploads u",
                        new JdbcProxySupport.StatementBehavior().withRows(List.of(JdbcProxySupport.row(
                                "id",
                                UPLOAD_ID,
                                "tenant_id",
                                TENANT_ID,
                                "repository_id",
                                REPOSITORY_ID,
                                "provider",
                                "github",
                                "commit_sha",
                                "abc123",
                                "branch",
                                "main",
                                "ignore_rules",
                                "[\"vendor/**\",\"!vendor/maintained/**\"]",
                                "pull_request_number",
                                null))))
                .whenSqlContains(
                        "from vericov.upload_artifacts",
                        new JdbcProxySupport.StatementBehavior().withRows(List.of(JdbcProxySupport.row(
                                "id",
                                ARTIFACT_ID,
                                "name",
                                "coverage.lcov",
                                "kind",
                                "coverage",
                                "format",
                                "lcov",
                                "storage_bucket",
                                "coverage-raw",
                                "storage_path",
                                "tenant/upload/coverage/coverage.lcov",
                                "sha256_hex",
                                "deadbeef"))));
        JdbcCoverageAnalysisInputRepository repository = new JdbcCoverageAnalysisInputRepository(dataSource);

        var input = repository.load(UPLOAD_ID);

        assertEquals(UPLOAD_ID, input.uploadId());
        assertEquals(TENANT_ID, input.tenantId());
        assertEquals(REPOSITORY_ID, input.repositoryId());
        assertEquals("github", input.provider());
        assertEquals("abc123", input.commitSha());
        assertEquals("main", input.branch());
        assertEquals(List.of("vendor/**", "!vendor/maintained/**"), input.ignore());
        assertEquals(null, input.pullRequestNumber());
        assertEquals(1, input.artifacts().size());
        assertEquals(ARTIFACT_ID, input.artifacts().getFirst().artifactId());
        assertEquals("coverage.lcov", input.artifacts().getFirst().name());
    }

    @Test
    void failsClearlyWhenUploadDoesNotExistOrJdbcFails() {
        JdbcCoverageAnalysisInputRepository missingUploadRepository = new JdbcCoverageAnalysisInputRepository(
                JdbcProxySupport.dataSource().whenSqlContains(
                        "from vericov.uploads u",
                        new JdbcProxySupport.StatementBehavior().withRows(List.of())));
        IllegalStateException missingUpload = assertThrows(
                IllegalStateException.class,
                () -> missingUploadRepository.load(UPLOAD_ID));
        assertEquals("Upload not found: " + UPLOAD_ID, missingUpload.getMessage());

        JdbcCoverageAnalysisInputRepository failingRepository = new JdbcCoverageAnalysisInputRepository(
                JdbcProxySupport.dataSource().whenSqlContains(
                        "from vericov.uploads u",
                        new JdbcProxySupport.StatementBehavior()
                                .withExecuteQueryException(new SQLException("db down"))));
        IllegalStateException jdbcFailure = assertThrows(
                IllegalStateException.class,
                () -> failingRepository.load(UPLOAD_ID));
        assertTrue(jdbcFailure.getMessage().contains("Failed to load coverage analysis input for upload " + UPLOAD_ID));
        assertTrue(jdbcFailure.getCause() instanceof SQLException);
    }

    @Test
    void rejectsInvalidPersistedIgnoreRulesAsNonRetryableInput() {
        JdbcProxySupport.RecordingDataSource dataSource = JdbcProxySupport.dataSource()
                .whenSqlContains(
                        "from vericov.uploads u",
                        new JdbcProxySupport.StatementBehavior().withRows(List.of(JdbcProxySupport.row(
                                "id", UPLOAD_ID,
                                "tenant_id", TENANT_ID,
                                "repository_id", REPOSITORY_ID,
                                "provider", "github",
                                "commit_sha", "abc123",
                                "branch", "main",
                                "ignore_rules", "[\"../secret.py\"]",
                                "pull_request_number", null))))
                .whenSqlContains(
                        "from vericov.upload_artifacts",
                        new JdbcProxySupport.StatementBehavior().withRows(List.of()));

        NonRetryableAnalysisException exception = assertThrows(
                NonRetryableAnalysisException.class,
                () -> new JdbcCoverageAnalysisInputRepository(dataSource).load(UPLOAD_ID));

        assertTrue(exception.getMessage().contains("ignore[0]"));
    }
}
