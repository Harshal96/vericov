package dev.vericov.analysis.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.analysis.diff.DiffCoverageFile;
import dev.vericov.analysis.diff.DiffCoverageLine;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.diff.DiffLineType;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcPrDiffCoverageRepositoryTest {
    @Test
    void savesParentFilesAndLinesInOneTransactionIncludingNullableFields() {
        JdbcProxySupport.StatementBehavior deleteBehavior = new JdbcProxySupport.StatementBehavior();
        JdbcProxySupport.StatementBehavior parentBehavior = new JdbcProxySupport.StatementBehavior();
        JdbcProxySupport.StatementBehavior filesBehavior = new JdbcProxySupport.StatementBehavior();
        JdbcProxySupport.StatementBehavior linesBehavior = new JdbcProxySupport.StatementBehavior();
        JdbcProxySupport.RecordingDataSource dataSource = JdbcProxySupport.dataSource()
                .whenSqlContains("delete from vericov.pull_request_coverage_diffs", deleteBehavior)
                .whenSqlContains("insert into vericov.pull_request_coverage_diffs", parentBehavior)
                .whenSqlContains("insert into vericov.pull_request_coverage_diff_files", filesBehavior)
                .whenSqlContains("insert into vericov.pull_request_coverage_diff_lines", linesBehavior);
        JdbcPrDiffCoverageRepository repository = new JdbcPrDiffCoverageRepository(dataSource);

        repository.save(
                UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf"),
                UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                42,
                "github",
                "complete",
                new DiffCoverageReport(
                        "base123",
                        "head456",
                        "complete",
                        7,
                        10,
                        2,
                        1,
                        List.of(new DiffCoverageFile(
                                "src/App.java",
                                null,
                                "modified",
                                7,
                                10,
                                2,
                                1,
                                List.of(
                                        new DiffCoverageLine(
                                                "src/App.java",
                                                null,
                                                12,
                                                14,
                                                DiffLineType.ADDED,
                                                true,
                                                0L,
                                                1L,
                                                true,
                                                false),
                                        new DiffCoverageLine(
                                                "src/App.java",
                                                "src/LegacyApp.java",
                                                null,
                                                null,
                                                DiffLineType.ADDED,
                                                false,
                                                null,
                                                null,
                                                false,
                                                true))))));

        assertFalse(dataSource.autoCommit());
        assertTrue(dataSource.committed());
        assertFalse(dataSource.rolledBack());
        assertEquals(1, filesBehavior.batchParameters().size());
        Map<Integer, Object> fileParams = filesBehavior.batchParameters().getFirst();
        assertTrue(JdbcProxySupport.isSqlNull(fileParams.get(5), JdbcProxySupport.varcharType()));
        assertEquals("modified", fileParams.get(6));

        assertEquals(2, linesBehavior.batchParameters().size());
        Map<Integer, Object> nullableLineParams = linesBehavior.batchParameters().get(1);
        assertEquals("src/LegacyApp.java", nullableLineParams.get(5));
        assertTrue(JdbcProxySupport.isSqlNull(nullableLineParams.get(6), JdbcProxySupport.integerType()));
        assertTrue(JdbcProxySupport.isSqlNull(nullableLineParams.get(7), JdbcProxySupport.integerType()));
        assertTrue(JdbcProxySupport.isSqlNull(nullableLineParams.get(10), JdbcProxySupport.bigintType()));
        assertTrue(JdbcProxySupport.isSqlNull(nullableLineParams.get(11), JdbcProxySupport.bigintType()));
    }

    @Test
    void rollsBackAndWrapsSqlFailures() {
        JdbcProxySupport.StatementBehavior linesBehavior = new JdbcProxySupport.StatementBehavior()
                .withExecuteBatchException(new SQLException("line insert failed"));
        JdbcProxySupport.RecordingDataSource dataSource = JdbcProxySupport.dataSource()
                .whenSqlContains("delete from vericov.pull_request_coverage_diffs", new JdbcProxySupport.StatementBehavior())
                .whenSqlContains("insert into vericov.pull_request_coverage_diffs", new JdbcProxySupport.StatementBehavior())
                .whenSqlContains("insert into vericov.pull_request_coverage_diff_files", new JdbcProxySupport.StatementBehavior())
                .whenSqlContains("insert into vericov.pull_request_coverage_diff_lines", linesBehavior);
        JdbcPrDiffCoverageRepository repository = new JdbcPrDiffCoverageRepository(dataSource);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> repository.save(
                        UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf"),
                        UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c"),
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        42,
                        "github",
                        "complete",
                        new DiffCoverageReport(
                                "base123",
                                "head456",
                                1,
                                1,
                                0,
                                0,
                                List.of(new DiffCoverageFile(
                                        "src/App.java",
                                        null,
                                        "modified",
                                        1,
                                        1,
                                        0,
                                        0,
                                        List.of(new DiffCoverageLine(
                                                "src/App.java",
                                                null,
                                                12,
                                                14,
                                                DiffLineType.ADDED,
                                                true,
                                                1L,
                                                1L,
                                                false,
                                                false)))))));

        assertTrue(dataSource.rolledBack());
        assertFalse(dataSource.committed());
        assertEquals("Failed to save pull request diff coverage", failure.getMessage());
        assertTrue(failure.getCause() instanceof SQLException);
    }
}
