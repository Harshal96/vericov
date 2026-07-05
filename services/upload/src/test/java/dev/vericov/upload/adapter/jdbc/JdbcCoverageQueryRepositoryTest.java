package dev.vericov.upload.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.upload.application.CoverageFileDetail;
import dev.vericov.upload.application.CoverageLineRange;
import dev.vericov.upload.application.ResolvedCoverageRef;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcCoverageQueryRepositoryTest {
    private static final UUID REPOSITORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPORT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID UPLOAD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String FULL_SHA = "a".repeat(40);

    @Test
    void resolvesByFullShaAgainstCommitShaColumn() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.reportRows.add(reportRow());
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        Optional<ResolvedCoverageRef> resolved = repository.resolveCoverageRef(REPOSITORY_ID, FULL_SHA);

        assertTrue(resolved.isPresent());
        assertEquals(REPORT_ID, resolved.get().reportId());
        assertTrue(dataSource.lastSql.contains("commit_sha = ?"));
    }

    @Test
    void resolvesByBranchNameWhenRefIsNotAFullSha() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.reportRows.add(reportRow());
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        Optional<ResolvedCoverageRef> resolved = repository.resolveCoverageRef(REPOSITORY_ID, "main");

        assertTrue(resolved.isPresent());
        assertTrue(dataSource.lastSql.contains("branch = ?"));
    }

    @Test
    void fallsBackToRepositoryDefaultBranchWhenRefIsBlank() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.repositoryRows.add(Map.of("default_branch", "develop"));
        dataSource.reportRows.add(reportRow());
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        Optional<ResolvedCoverageRef> resolved = repository.resolveCoverageRef(REPOSITORY_ID, null);

        assertTrue(resolved.isPresent());
    }

    @Test
    void missingReportReturnsEmpty() {
        RecordingDataSource dataSource = new RecordingDataSource();
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        assertTrue(repository.resolveCoverageRef(REPOSITORY_ID, "main").isEmpty());
    }

    @Test
    void fileDetailCollapsesConsecutiveUncoveredLinesIntoRanges() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.fileSummaryRows.add(row(
                "file_path", "src/Main.java",
                "leaf_component_key", "api",
                "owners", new String[] {"team-api"},
                "line_covered", 5L, "line_total", 10L,
                "branch_covered", 1L, "branch_total", 2L,
                "function_covered", 1L, "function_total", 1L,
                "statement_covered", 5L, "statement_total", 10L));
        dataSource.lineHitRows.add(Map.of("line_number", 3));
        dataSource.lineHitRows.add(Map.of("line_number", 4));
        dataSource.lineHitRows.add(Map.of("line_number", 8));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        Optional<CoverageFileDetail> detail = repository.fileDetail(REPORT_ID, "src/Main.java");

        assertTrue(detail.isPresent());
        assertEquals(
                List.of(new CoverageLineRange(3, 4), new CoverageLineRange(8, 8)),
                detail.get().uncoveredRanges());
    }

    @Test
    void fileDetailReturnsEmptyWhenFileDoesNotExistInReport() {
        RecordingDataSource dataSource = new RecordingDataSource();
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        assertTrue(repository.fileDetail(REPORT_ID, "missing.java").isEmpty());
    }

    @Test
    void filesForReturnsFileSummaries() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.fileSummaryRows.add(row(
                "file_path", "src/Main.java",
                "leaf_component_key", "api",
                "owners", new String[] {"team-api"},
                "line_covered", 5L, "line_total", 10L,
                "branch_covered", 1L, "branch_total", 2L,
                "function_covered", 1L, "function_total", 1L,
                "statement_covered", 5L, "statement_total", 10L));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        var files = repository.filesFor(REPORT_ID, null, null, null, 100, 0);

        assertEquals(1, files.size());
        assertEquals("src/Main.java", files.get(0).filePath());
        assertEquals("api", files.get(0).leafComponentKey());
    }

    @Test
    void gapsForReturnsGapFindingsFilteredByMinRiskLevel() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.gapFindingRows.add(row(
                "file_path", "a.java", "target_type", "line", "line_start", 3, "line_end", 3,
                "symbol_name", null, "reason_code", "uncovered_executable_line", "explanation", "explanation",
                "confidence", "high", "risk_score", new java.math.BigDecimal("70.0"), "risk_level", "high",
                "owners", new String[] {"team-a"}, "component_key", "api", "next_action", "add_test",
                "status", "active"));
        dataSource.gapFindingRows.add(row(
                "file_path", "b.java", "target_type", "line", "line_start", 4, "line_end", 4,
                "symbol_name", null, "reason_code", "uncovered_executable_line", "explanation", "explanation",
                "confidence", "low", "risk_score", new java.math.BigDecimal("20.0"), "risk_level", "low",
                "owners", new String[0], "component_key", "api", "next_action", "add_test",
                "status", "active"));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        var allGaps = repository.gapsFor(REPORT_ID, null, null, null, 100, 0);
        var highRiskOnly = repository.gapsFor(REPORT_ID, null, "high", null, 100, 0);

        assertEquals(2, allGaps.size());
        assertEquals(1, highRiskOnly.size());
        assertEquals("a.java", highRiskOnly.get(0).filePath());
    }

    @Test
    void gatesForReturnsGateEvaluations() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.gateEvaluationRows.add(row(
                "gate_name", "line-gate", "gate_type", "line_coverage", "metric", "line",
                "scope_type", "repository", "scope_key", null, "scope_path", new String[0],
                "threshold", new java.math.BigDecimal("80"), "actual", new java.math.BigDecimal("75"),
                "status", "failed", "blocking", true));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        var gates = repository.gatesFor(REPORT_ID);

        assertEquals(1, gates.size());
        assertEquals("failed", gates.get(0).status());
        assertTrue(gates.get(0).blocking());
    }

    @Test
    void patchForPullRequestFindsTheLatestDiffBearingReport() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.pullRequestDiffLookupRows.add(Map.of("coverage_report_id", REPORT_ID));
        dataSource.pullRequestDiffRows.add(row(
                "id", UUID.randomUUID(), "status", "complete", "base_sha", "base-sha", "head_sha", "head-sha",
                "patch_line_covered", 4, "patch_line_total", 5,
                "newly_missed_line_count", 0, "lost_coverage_line_count", 1));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        Optional<dev.vericov.upload.application.PatchCoverageDetails> patch =
                repository.patchForPullRequest(REPOSITORY_ID, 42);

        assertTrue(patch.isPresent());
        assertEquals("complete", patch.get().status());
        assertEquals(4, patch.get().lineCovered());
    }

    @Test
    void patchForPullRequestReturnsEmptyWhenNoDiffExists() {
        RecordingDataSource dataSource = new RecordingDataSource();
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        assertTrue(repository.patchForPullRequest(REPOSITORY_ID, 42).isEmpty());
    }

    @Test
    void repositoryInfoReturnsFullNameAndDefaultBranch() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.repositoryRows.add(Map.of("full_name", "acme/api", "default_branch", "main"));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        var info = repository.repositoryInfo(REPOSITORY_ID);

        assertTrue(info.isPresent());
        assertEquals("acme/api", info.get().fullName());
        assertEquals("main", info.get().defaultBranch());
    }

    @Test
    void resolveCoverageRefForPullRequestFindsTheLatestDiffBearingReport() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.pullRequestDiffLookupRows.add(reportRow());
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        var resolved = repository.resolveCoverageRefForPullRequest(REPOSITORY_ID, 481);

        assertTrue(resolved.isPresent());
        assertEquals(REPORT_ID, resolved.get().reportId());
    }

    @Test
    void gapManifestEntriesResolvesRiskFactorsRangesAndInPatchFlag() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.diffFileRows.add(Map.of("file_path", "src/Retry.java"));
        dataSource.gapFindingRows.add(row(
                "id", UUID.randomUUID(),
                "file_path", "src/Retry.java",
                "target_type", "range",
                "line_start", 84,
                "line_end", 97,
                "symbol_name", null,
                "reason_code", "new_uncovered_changed_line",
                "explanation", "explanation",
                "confidence", "high",
                "risk_score", new java.math.BigDecimal("78.0"),
                "risk_level", "high",
                "owners", new String[] {"team-payments"},
                "component_key", "payments-api",
                "next_action", "add_test",
                "evidence_json",
                "{\"score\":{\"total\":78.0,\"level\":\"high\","
                        + "\"factors\":[{\"name\":\"change_exposure\",\"value\":25,"
                        + "\"reason\":\"new_uncovered_changed_line\"}]}}"));
        dataSource.lineHitRows.add(Map.of("line_number", 84));
        dataSource.lineHitRows.add(Map.of("line_number", 85));
        dataSource.lineHitRows.add(Map.of("line_number", 95));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        var entries = repository.gapManifestEntries(REPORT_ID, null, null, 100, 0);

        assertEquals(1, entries.size());
        var entry = entries.get(0);
        assertEquals(1, entry.rank());
        assertTrue(entry.inPatch());
        assertEquals(1, entry.riskFactors().size());
        assertTrue(entry.riskFactors().get(0).contains("change_exposure"));
        assertEquals(
                List.of(new dev.vericov.upload.application.CoverageLineRange(84, 85),
                        new dev.vericov.upload.application.CoverageLineRange(95, 95)),
                entry.uncoveredRanges());
    }

    @Test
    void gapManifestEntriesFallsBackToEmptyFactorsForMalformedEvidence() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.gapFindingRows.add(row(
                "id", UUID.randomUUID(),
                "file_path", "src/Retry.java",
                "target_type", "line",
                "line_start", 1,
                "line_end", 1,
                "symbol_name", null,
                "reason_code", "uncovered_executable_line",
                "explanation", "explanation",
                "confidence", "low",
                "risk_score", new java.math.BigDecimal("10.0"),
                "risk_level", "low",
                "owners", new String[0],
                "component_key", null,
                "next_action", "add_test",
                "evidence_json", "not json"));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        var entries = repository.gapManifestEntries(REPORT_ID, null, null, 100, 0);

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).riskFactors().isEmpty());
        assertFalse(entries.get(0).inPatch());
    }

    private static Map<String, Object> reportRow() {
        return row(
                "id", REPORT_ID,
                "upload_id", UPLOAD_ID,
                "repository_id", REPOSITORY_ID,
                "commit_sha", FULL_SHA,
                "branch", "main",
                "created_at", OffsetDateTime.ofInstant(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC));
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<Map<String, Object>> reportRows = new ArrayList<>();
        private final List<Map<String, Object>> repositoryRows = new ArrayList<>();
        private final List<Map<String, Object>> fileSummaryRows = new ArrayList<>();
        private final List<Map<String, Object>> lineHitRows = new ArrayList<>();
        private final List<Map<String, Object>> gapFindingRows = new ArrayList<>();
        private final List<Map<String, Object>> gateEvaluationRows = new ArrayList<>();
        private final List<Map<String, Object>> pullRequestDiffLookupRows = new ArrayList<>();
        private final List<Map<String, Object>> pullRequestDiffRows = new ArrayList<>();
        private final List<Map<String, Object>> diffFileRows = new ArrayList<>();
        private String lastSql = "";

        @Override
        public Connection getConnection() {
            InvocationHandler handler = (proxy, method, args) -> connectionMethod(method, args);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
        }

        private Object connectionMethod(Method method, Object[] args) {
            return switch (method.getName()) {
                case "prepareStatement" -> preparedStatement((String) args[0]);
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement preparedStatement(String sql) {
            lastSql = sql;
            InvocationHandler handler = (proxy, method, args) -> preparedStatementMethod(sql, method);
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class}, handler);
        }

        private Object preparedStatementMethod(String sql, Method method) {
            if (!"executeQuery".equals(method.getName())) {
                return defaultValue(method.getReturnType());
            }
            if (sql.contains("from vericov.repositories")) {
                return resultSet(repositoryRows);
            }
            if (sql.contains("join vericov.coverage_reports cr")) {
                return resultSet(pullRequestDiffLookupRows);
            }
            if (sql.contains("from vericov.coverage_reports")) {
                return resultSet(reportRows);
            }
            if (sql.contains("from vericov.coverage_line_hits")) {
                return resultSet(lineHitRows);
            }
            if (sql.contains("from vericov.coverage_file_summaries")) {
                return resultSet(fileSummaryRows);
            }
            if (sql.contains("from vericov.coverage_gap_findings")) {
                return resultSet(gapFindingRows);
            }
            if (sql.contains("from vericov.gate_evaluations")) {
                return resultSet(gateEvaluationRows);
            }
            if (sql.contains("pull_request_coverage_diff_files")) {
                return resultSet(diffFileRows);
            }
            if (sql.contains("from vericov.pull_request_coverage_diffs")) {
                return resultSet(pullRequestDiffRows);
            }
            return resultSet(List.of());
        }

        private static ResultSet resultSet(List<Map<String, Object>> rows) {
            final int[] index = {-1};
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "next" -> ++index[0] < rows.size();
                case "getString" -> value(rows, index[0], args);
                case "getInt" -> {
                    Object value = value(rows, index[0], args);
                    yield value == null ? 0 : ((Number) value).intValue();
                }
                case "getLong" -> {
                    Object value = value(rows, index[0], args);
                    yield value == null ? 0L : ((Number) value).longValue();
                }
                case "getObject" -> value(rows, index[0], args);
                case "getBigDecimal" -> value(rows, index[0], args);
                case "getBoolean" -> {
                    Object value = value(rows, index[0], args);
                    yield value != null && (boolean) value;
                }
                case "getArray" -> {
                    Object value = value(rows, index[0], args);
                    yield value == null ? null : array(value);
                }
                case "wasNull" -> false;
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class}, handler);
        }

        private static Object value(List<Map<String, Object>> rows, int index, Object[] args) {
            if (args[0] instanceof String column) {
                return rows.get(index).get(column);
            }
            return null;
        }

        private static java.sql.Array array(Object value) {
            return (java.sql.Array) Proxy.newProxyInstance(
                    java.sql.Array.class.getClassLoader(),
                    new Class<?>[] {java.sql.Array.class},
                    (proxy, method, args) -> "getArray".equals(method.getName()) ? value : null);
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class || type == long.class) {
                return 0;
            }
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
