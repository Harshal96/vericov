# Gate Evaluation After Report Processing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `services/coverage-analysis` evaluate active repository coverage gates after a coverage report is processed and persist `vericov.gate_evaluations` rows that the Organization service already exposes.

**Architecture:** Coverage Analysis evaluates gates at the point it has a normalized `CoverageReport`, then saves the report, file summaries, gate evaluations, upload status, and upload events in one JDBC transaction. The first production slice evaluates active `project_coverage` gates from report-level line, branch, function, and statement percentages; later slices can add patch, component, mutation, and agent-review inputs without changing the read API. Gate configuration is read behind a `GateConfigurationRepository` port so the initial JDBC reader can later be replaced by the intended internal effective-config client when service-token auth lands in Organization.

**Tech Stack:** Java 25, Helidon 4 MP, JDBC/Postgres, JSON-P, JUnit 5, Cucumber JVM, Supabase Postgres in the `vericov` schema.

---

## Current Findings

- `DefaultCoverageAnalysisProcessor.process(...)` loads upload input, downloads LCOV artifacts, merges them, and calls `CoverageReportRepository.save(report)`.
- `JdbcCoverageReportRepository.save(...)` deletes any existing report for the same upload, inserts `coverage_reports`, inserts `coverage_file_summaries`, marks the upload processed, and emits `coverage.report.completed`.
- `vericov.gate_evaluations` already exists and Organization can list it through `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/gate-evaluations`.
- `repository_gate_configurations` already exists, with active/disabled status, gate types, metrics, thresholds, `max_drop`, blocking mode, and `config_json`.
- Important persistence hazard: `gate_evaluations.coverage_report_id` uses `ON DELETE SET NULL`, so reprocessing an upload must explicitly delete old gate rows before deleting the old coverage report.

## Scope

Implement in this plan:

- Evaluate active `project_coverage` gates for metrics `line`, `branch`, `function`, and `statement`.
- Persist one `gate_evaluations` row per evaluated gate.
- Use status semantics:
  - `passed` when `actual >= threshold`.
  - `failed` when below threshold and `blocking=true`.
  - `warning` when below threshold and `blocking=false`.
- Ignore disabled gates.
- Skip non-`project_coverage` gate types until their required inputs exist.
- Record details JSON with the source report id, scope, covered count, total count, percentage, and threshold.

Do not implement in this plan:

- Patch coverage gates; they need PR diff/changed-line data.
- Component coverage gates; they need component/path selection semantics.
- Mutation score gates; they need mutation report ingestion.
- Agent review gates; they need agent-run outputs.
- Service-token auth for Organization's internal effective-config endpoint.

## File Structure

- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateConfiguration.java`
  - Immutable analysis-local view of `repository_gate_configurations`.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateEvaluation.java`
  - Immutable analysis-local write model for `gate_evaluations`.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateEvaluator.java`
  - Pure domain evaluator for `project_coverage` gates.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/GateConfigurationRepository.java`
  - Port for loading active gate config.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/AnalysisJsonCodec.java`
  - Small JSON object codec for `config_json` and `details_json`.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcGateConfigurationRepository.java`
  - JDBC adapter for active repository gates.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/CoverageReportRepository.java`
  - Add a save overload that accepts gate evaluations.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`
  - Load gates, evaluate them, and save report plus evaluations.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java`
  - Persist gate evaluations transactionally and clean old rows on reprocessing.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`
  - Wire the JDBC gate config repository into the processor.
- Modify tests:
  - `services/coverage-analysis/src/test/java/dev/vericov/analysis/gates/GateEvaluatorTest.java`
  - `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`
  - `services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java`
  - `services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature`
- Modify docs:
  - `docs/backend/services/04-coverage-analysis-service.md`
  - `infra/supabase/README.md`

---

### Task 1: Add Gate Evaluation Domain

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateConfiguration.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateEvaluation.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateEvaluator.java`
- Test: `services/coverage-analysis/src/test/java/dev/vericov/analysis/gates/GateEvaluatorTest.java`

- [ ] **Step 1: Write the failing evaluator test**

Create `services/coverage-analysis/src/test/java/dev/vericov/analysis/gates/GateEvaluatorTest.java`:

```java
package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GateEvaluatorTest {
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID ORG_ID = UUID.fromString("2ca9c094-7c28-4cb9-9b99-aae95cf07050");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID REPORT_ID = UUID.fromString("46d061c7-b160-4550-b7a4-5e0b81821621");
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");

    @Test
    void evaluatesActiveProjectCoverageGates() {
        GateEvaluator evaluator = new GateEvaluator();

        List<GateEvaluation> evaluations = evaluator.evaluate(report(), List.of(
                gate("line-minimum", "project_coverage", "line", "80.0", true, "active"),
                gate("branch-minimum", "project_coverage", "branch", "80.0", true, "active"),
                gate("statement-advisory", "project_coverage", "statement", "95.0", false, "active"),
                gate("disabled", "project_coverage", "line", "100.0", true, "disabled"),
                gate("patch", "patch_coverage", "line", "90.0", true, "active")), NOW);

        assertEquals(3, evaluations.size());

        GateEvaluation line = evaluations.get(0);
        assertEquals("line-minimum", line.gateName());
        assertEquals("passed", line.status());
        assertEquals(new BigDecimal("80.0000"), line.threshold());
        assertEquals(new BigDecimal("85.0000"), line.actual());
        assertEquals(REPORT_ID, line.coverageReportId());
        assertEquals("project", line.details().get("scope"));
        assertEquals(17, line.details().get("covered"));
        assertEquals(20, line.details().get("total"));

        GateEvaluation branch = evaluations.get(1);
        assertEquals("branch-minimum", branch.gateName());
        assertEquals("failed", branch.status());
        assertEquals(new BigDecimal("50.0000"), branch.actual());

        GateEvaluation statement = evaluations.get(2);
        assertEquals("statement-advisory", statement.gateName());
        assertEquals("warning", statement.status());
        assertEquals(new BigDecimal("85.0000"), statement.actual());
    }

    @Test
    void emitsWarningWhenProjectGateUsesUnsupportedReportMetric() {
        GateEvaluator evaluator = new GateEvaluator();

        List<GateEvaluation> evaluations = evaluator.evaluate(report(), List.of(
                gate("mutation", "project_coverage", "mutation", "80.0", true, "active")), NOW);

        assertEquals(1, evaluations.size());
        GateEvaluation evaluation = evaluations.getFirst();
        assertEquals("warning", evaluation.status());
        assertNull(evaluation.actual());
        assertEquals("metric_not_available_in_coverage_report", evaluation.details().get("reason"));
    }

    private static GateConfiguration gate(
            String name,
            String gateType,
            String metric,
            String threshold,
            boolean blocking,
            String status) {
        return new GateConfiguration(
                UUID.nameUUIDFromBytes(name.getBytes()),
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                name,
                gateType,
                metric,
                new BigDecimal(threshold),
                null,
                blocking,
                Map.of(),
                status);
    }

    private static CoverageReport report() {
        return new CoverageReport(
                REPORT_ID,
                UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6"),
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                new CoverageMetric(17, 20),
                new CoverageMetric(1, 2),
                new CoverageMetric(3, 3),
                new CoverageMetric(17, 20),
                List.of(new CoverageFileSummary(
                        "src/App.java",
                        new CoverageMetric(17, 20),
                        new CoverageMetric(1, 2),
                        new CoverageMetric(3, 3),
                        new CoverageMetric(17, 20))),
                NOW);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl services/coverage-analysis test -Dtest=GateEvaluatorTest
```

Expected: FAIL because `dev.vericov.analysis.gates` classes do not exist.

- [ ] **Step 3: Implement the domain records and evaluator**

Create `GateConfiguration.java`:

```java
package dev.vericov.analysis.gates;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GateConfiguration(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status) {

    public GateConfiguration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(gateType, "gateType");
        Objects.requireNonNull(metric, "metric");
        config = Map.copyOf(config == null ? Map.of() : config);
        Objects.requireNonNull(status, "status");
    }

    public boolean active() {
        return "active".equals(status);
    }
}
```

Create `GateEvaluation.java`:

```java
package dev.vericov.analysis.gates;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GateEvaluation(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        UUID coverageReportId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String gateName,
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking,
        Map<String, Object> details,
        Instant evaluatedAt) {

    public GateEvaluation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(coverageReportId, "coverageReportId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(gateName, "gateName");
        Objects.requireNonNull(gateType, "gateType");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(status, "status");
        details = Map.copyOf(details == null ? Map.of() : details);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }
}
```

Create `GateEvaluator.java`:

```java
package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GateEvaluator {

    public List<GateEvaluation> evaluate(
            CoverageReport report,
            List<GateConfiguration> gates,
            Instant evaluatedAt) {
        return gates.stream()
                .filter(GateConfiguration::active)
                .filter(gate -> "project_coverage".equals(gate.gateType()))
                .map(gate -> evaluateProjectCoverage(report, gate, evaluatedAt))
                .toList();
    }

    private static GateEvaluation evaluateProjectCoverage(
            CoverageReport report,
            GateConfiguration gate,
            Instant evaluatedAt) {
        Optional<CoverageMetric> metric = reportMetric(report, gate.metric());
        if (metric.isEmpty()) {
            return evaluation(report, gate, null, "warning", Map.of(
                    "scope", "project",
                    "reason", "metric_not_available_in_coverage_report",
                    "coverage_report_id", report.reportId().toString()));
        }

        CoverageMetric coverageMetric = metric.get();
        BigDecimal actual = percentage(coverageMetric);
        String status = actual.compareTo(normalize(gate.threshold())) >= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "project");
        details.put("coverage_report_id", report.reportId().toString());
        details.put("covered", coverageMetric.covered());
        details.put("total", coverageMetric.total());
        details.put("percentage", actual);
        details.put("threshold", normalize(gate.threshold()));
        return evaluation(report, gate, actual, status, details);
    }

    private static GateEvaluation evaluation(
            CoverageReport report,
            GateConfiguration gate,
            BigDecimal actual,
            String status,
            Map<String, Object> details) {
        return new GateEvaluation(
                UUID.randomUUID(),
                report.tenantId(),
                gate.organizationId(),
                report.repositoryId(),
                report.reportId(),
                report.commitSha(),
                report.branchName(),
                report.pullRequestNumber(),
                gate.name(),
                gate.gateType(),
                gate.metric(),
                normalize(gate.threshold()),
                actual,
                status,
                gate.blocking(),
                details,
                report.generatedAt());
    }

    private static Optional<CoverageMetric> reportMetric(CoverageReport report, String metric) {
        return switch (metric) {
            case "line" -> Optional.of(report.line());
            case "branch" -> Optional.of(report.branch());
            case "function" -> Optional.of(report.function());
            case "statement" -> Optional.of(report.statement());
            default -> Optional.empty();
        };
    }

    private static BigDecimal percentage(CoverageMetric metric) {
        return BigDecimal.valueOf(metric.percentage()).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 4: Run the domain test**

Run:

```bash
mvn -pl services/coverage-analysis test -Dtest=GateEvaluatorTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/coverage-analysis/src/main/java/dev/vericov/analysis/gates services/coverage-analysis/src/test/java/dev/vericov/analysis/gates
git commit -m "feat: add coverage gate evaluator"
```

---

### Task 2: Load Active Gate Configurations

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/GateConfigurationRepository.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/AnalysisJsonCodec.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcGateConfigurationRepository.java`

- [ ] **Step 1: Create the port**

Create `GateConfigurationRepository.java`:

```java
package dev.vericov.analysis.application.port;

import dev.vericov.analysis.gates.GateConfiguration;
import java.util.List;
import java.util.UUID;

public interface GateConfigurationRepository {
    List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId);
}
```

- [ ] **Step 2: Create the JSON codec**

Create `AnalysisJsonCodec.java` by copying the small JSON object pattern from `services/integrations/.../IntegrationJsonCodec.java`, but throw `IllegalArgumentException` instead of service-specific exceptions.

Required public methods:

```java
public String toJsonObject(Map<String, Object> values)
public Map<String, Object> fromJsonObject(String raw)
public Map<String, Object> jsonObject(ResultSet resultSet, String columnName) throws SQLException
```

The codec must support `String`, `Boolean`, integral numbers, `BigDecimal`, nested `Map`, `List`, and `null`.

- [ ] **Step 3: Implement the JDBC adapter**

Create `JdbcGateConfigurationRepository.java`:

```java
package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.gates.GateConfiguration;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcGateConfigurationRepository implements GateConfigurationRepository {
    private final DataSource dataSource;
    private final AnalysisJsonCodec codec = new AnalysisJsonCodec();

    public JdbcGateConfigurationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, name, gate_type, metric,
                               threshold, max_drop, blocking, config_json, status
                        from vericov.repository_gate_configurations
                        where tenant_id = ?
                          and repository_id = ?
                          and status = 'active'
                        order by name, id
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<GateConfiguration> gates = new ArrayList<>();
                while (resultSet.next()) {
                    gates.add(new GateConfiguration(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getObject("tenant_id", UUID.class),
                            resultSet.getObject("org_id", UUID.class),
                            resultSet.getObject("repository_id", UUID.class),
                            resultSet.getString("name"),
                            resultSet.getString("gate_type"),
                            resultSet.getString("metric"),
                            resultSet.getBigDecimal("threshold"),
                            resultSet.getBigDecimal("max_drop"),
                            resultSet.getBoolean("blocking"),
                            codec.jsonObject(resultSet, "config_json"),
                            resultSet.getString("status")));
                }
                return List.copyOf(gates);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load gate configuration for repository " + repositoryId, exception);
        }
    }
}
```

- [ ] **Step 4: Run compilation for coverage-analysis**

Run:

```bash
mvn -pl services/coverage-analysis test -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/GateConfigurationRepository.java services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/AnalysisJsonCodec.java services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcGateConfigurationRepository.java
git commit -m "feat: load coverage gate configuration"
```

---

### Task 3: Wire Evaluation Into Report Processing

**Files:**
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`

- [ ] **Step 1: Write the failing processor test**

Extend `DefaultCoverageAnalysisProcessorTest.downloadsLcovArtifactsAndPersistsMergedCoverageReport` so it uses a fake gate repository and asserts one saved evaluation:

```java
FakeGateConfigurationRepository gates = new FakeGateConfigurationRepository(List.of(new GateConfiguration(
        UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
        TENANT_ID,
        UUID.fromString("2ca9c094-7c28-4cb9-9b99-aae95cf07050"),
        REPOSITORY_ID,
        "line-minimum",
        "project_coverage",
        "line",
        new BigDecimal("90.0"),
        null,
        true,
        Map.of(),
        "active")));
FakeReportRepository reports = new FakeReportRepository();
DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
        inputs,
        content,
        reports,
        gates,
        new LcovCoverageParser(),
        Clock.fixed(NOW, ZoneOffset.UTC));

processor.process(event());

assertEquals(1, reports.savedEvaluations.size());
GateEvaluation evaluation = reports.savedEvaluations.getFirst();
assertEquals("line-minimum", evaluation.gateName());
assertEquals("passed", evaluation.status());
assertEquals(new BigDecimal("100.0000"), evaluation.actual());
```

Add imports:

```java
import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.gates.GateConfiguration;
import dev.vericov.analysis.gates.GateEvaluation;
import java.math.BigDecimal;
```

Update `FakeReportRepository`:

```java
private static final class FakeReportRepository implements CoverageReportRepository {
    private CoverageReport savedReport;
    private List<GateEvaluation> savedEvaluations = List.of();

    @Override
    public void save(CoverageReport report) {
        savedReport = report;
    }

    @Override
    public void save(CoverageReport report, List<GateEvaluation> evaluations) {
        savedReport = report;
        savedEvaluations = List.copyOf(evaluations);
    }
}
```

Add fake gate repository:

```java
private record FakeGateConfigurationRepository(List<GateConfiguration> gates)
        implements GateConfigurationRepository {
    @Override
    public List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId) {
        assertEquals(TENANT_ID, tenantId);
        assertEquals(REPOSITORY_ID, repositoryId);
        return gates;
    }
}
```

- [ ] **Step 2: Run the processor test to verify it fails**

Run:

```bash
mvn -pl services/coverage-analysis test -Dtest=DefaultCoverageAnalysisProcessorTest
```

Expected: FAIL because the processor constructor and repository port do not accept gate dependencies yet.

- [ ] **Step 3: Add the save overload**

Modify `CoverageReportRepository.java`:

```java
package dev.vericov.analysis.application.port;

import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.gates.GateEvaluation;
import java.util.List;

public interface CoverageReportRepository {
    void save(CoverageReport report);

    default void save(CoverageReport report, List<GateEvaluation> evaluations) {
        save(report);
    }
}
```

- [ ] **Step 4: Wire the processor**

Modify `DefaultCoverageAnalysisProcessor.java`:

```java
private final GateConfigurationRepository gates;
private final GateEvaluator gateEvaluator = new GateEvaluator();
```

Update constructor parameters:

```java
public DefaultCoverageAnalysisProcessor(
        CoverageAnalysisInputRepository inputs,
        ArtifactContentStore contentStore,
        CoverageReportRepository reports,
        GateConfigurationRepository gates,
        LcovCoverageParser lcovParser,
        Clock clock) {
    this.inputs = Objects.requireNonNull(inputs, "inputs");
    this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
    this.reports = Objects.requireNonNull(reports, "reports");
    this.gates = Objects.requireNonNull(gates, "gates");
    this.lcovParser = Objects.requireNonNull(lcovParser, "lcovParser");
    this.clock = Objects.requireNonNull(clock, "clock");
}
```

After merging the report:

```java
CoverageReport report = merger.merge(input, parsedCoverages, clock.instant());
List<GateEvaluation> evaluations = gateEvaluator.evaluate(
        report,
        gates.listActiveForRepository(report.tenantId(), report.repositoryId()),
        report.generatedAt());
reports.save(report, evaluations);
```

- [ ] **Step 5: Run processor tests**

Run:

```bash
mvn -pl services/coverage-analysis test -Dtest=DefaultCoverageAnalysisProcessorTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/CoverageReportRepository.java services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java
git commit -m "feat: evaluate gates during coverage processing"
```

---

### Task 4: Persist Gate Evaluations Transactionally

**Files:**
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java`

- [ ] **Step 1: Override the new save method**

Add import:

```java
import dev.vericov.analysis.gates.GateEvaluation;
import java.util.List;
```

Change `save(CoverageReport report)` to delegate:

```java
@Override
public void save(CoverageReport report) {
    save(report, List.of());
}
```

Add:

```java
@Override
public void save(CoverageReport report, List<GateEvaluation> evaluations) {
    try (var connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        try {
            deleteExistingGateEvaluations(connection, report);
            deleteExistingReport(connection, report);
            insertCoverageReport(connection, report);
            insertFileSummaries(connection, report);
            insertGateEvaluations(connection, evaluations);
            markUploadProcessed(connection, report);
            insertReportCompletedEvent(connection, report);
            insertGatesEvaluatedEvent(connection, report, evaluations);
            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly(connection);
            throw exception;
        }
    } catch (SQLException exception) {
        throw new IllegalStateException("Failed to save coverage report for upload " + report.uploadId(), exception);
    }
}
```

- [ ] **Step 2: Delete old gate rows before replacing a report**

Add before `deleteExistingReport(...)`:

```java
private static void deleteExistingGateEvaluations(java.sql.Connection connection, CoverageReport report) throws SQLException {
    try (var statement = connection.prepareStatement("""
            delete from vericov.gate_evaluations
            where coverage_report_id in (
                select id
                from vericov.coverage_reports
                where upload_id = ?
            )
            """)) {
        statement.setObject(1, report.uploadId());
        statement.executeUpdate();
    }
}
```

- [ ] **Step 3: Insert gate evaluations**

Add an `AnalysisJsonCodec` field:

```java
private final AnalysisJsonCodec codec = new AnalysisJsonCodec();
```

Add:

```java
private void insertGateEvaluations(
        java.sql.Connection connection,
        List<GateEvaluation> evaluations) throws SQLException {
    if (evaluations.isEmpty()) {
        return;
    }
    try (var statement = connection.prepareStatement("""
            insert into vericov.gate_evaluations (
                id,
                tenant_id,
                org_id,
                repository_id,
                coverage_report_id,
                commit_sha,
                branch,
                pull_request_number,
                gate_name,
                gate_type,
                metric,
                threshold,
                actual,
                status,
                blocking,
                details_json,
                evaluated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """)) {
        for (GateEvaluation evaluation : evaluations) {
            int index = 1;
            statement.setObject(index++, evaluation.id());
            statement.setObject(index++, evaluation.tenantId());
            statement.setObject(index++, evaluation.organizationId());
            statement.setObject(index++, evaluation.repositoryId());
            statement.setObject(index++, evaluation.coverageReportId());
            statement.setString(index++, evaluation.commitSha());
            statement.setString(index++, evaluation.branch());
            if (evaluation.pullRequestNumber() == null) {
                statement.setNull(index++, Types.INTEGER);
            } else {
                statement.setInt(index++, evaluation.pullRequestNumber());
            }
            statement.setString(index++, evaluation.gateName());
            statement.setString(index++, evaluation.gateType());
            statement.setString(index++, evaluation.metric());
            statement.setBigDecimal(index++, evaluation.threshold());
            statement.setBigDecimal(index++, evaluation.actual());
            statement.setString(index++, evaluation.status());
            statement.setBoolean(index++, evaluation.blocking());
            statement.setString(index++, codec.toJsonObject(evaluation.details()));
            statement.setObject(index++, utc(evaluation.evaluatedAt()));
            statement.addBatch();
        }
        statement.executeBatch();
    }
}
```

- [ ] **Step 4: Emit a gate event after report completion**

Add:

```java
private static void insertGatesEvaluatedEvent(
        java.sql.Connection connection,
        CoverageReport report,
        List<GateEvaluation> evaluations) throws SQLException {
    if (evaluations.isEmpty()) {
        return;
    }
    long failed = evaluations.stream().filter(evaluation -> "failed".equals(evaluation.status())).count();
    long warnings = evaluations.stream().filter(evaluation -> "warning".equals(evaluation.status())).count();
    try (var statement = connection.prepareStatement("""
            insert into vericov.upload_events (
                tenant_id,
                upload_id,
                event_type,
                payload,
                created_at
            )
            values (
                ?,
                ?,
                'coverage.gates.evaluated',
                jsonb_build_object(
                    'coverage_report_id', ?,
                    'gate_evaluation_count', ?,
                    'failed_gate_count', ?,
                    'warning_gate_count', ?
                ),
                ?
            )
            """)) {
        int index = 1;
        statement.setObject(index++, report.tenantId());
        statement.setObject(index++, report.uploadId());
        statement.setString(index++, report.reportId().toString());
        statement.setInt(index++, evaluations.size());
        statement.setLong(index++, failed);
        statement.setLong(index++, warnings);
        statement.setObject(index, utc(report.generatedAt()));
        statement.executeUpdate();
    }
}
```

- [ ] **Step 5: Run compilation**

Run:

```bash
mvn -pl services/coverage-analysis test -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java
git commit -m "feat: persist gate evaluations with coverage reports"
```

---

### Task 5: Wire Runtime Components and BDD Coverage

**Files:**
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java`
- Modify: `services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature`

- [ ] **Step 1: Wire the JDBC gate config adapter**

Modify `AnalysisComponents.coverageAnalysisProcessor()`:

```java
return new DefaultCoverageAnalysisProcessor(
        new JdbcCoverageAnalysisInputRepository(dataSource),
        new HttpSupabaseArtifactContentStore(
                supabaseStorageBaseUri(),
                requiredEnv("SUPABASE_SERVICE_ROLE_KEY")),
        new JdbcCoverageReportRepository(dataSource),
        new JdbcGateConfigurationRepository(dataSource),
        new LcovCoverageParser(),
        Clock.systemUTC());
```

Add import:

```java
import dev.vericov.analysis.adapter.jdbc.JdbcGateConfigurationRepository;
```

- [ ] **Step 2: Add BDD gate setup and assertion**

In `AnalysisSteps`, add:

```java
private final FakeGateConfigurationRepository gates = new FakeGateConfigurationRepository();
```

Add step:

```java
@Given("an active project coverage gate requiring {int} percent line coverage")
public void activeProjectCoverageGateRequiringPercentLineCoverage(int threshold) {
    gates.gates = List.of(new GateConfiguration(
            UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
            TENANT_ID,
            UUID.fromString("2ca9c094-7c28-4cb9-9b99-aae95cf07050"),
            REPOSITORY_ID,
            "line-minimum",
            "project_coverage",
            "line",
            new BigDecimal(threshold),
            null,
            true,
            Map.of(),
            "active"));
}
```

Update `processor()`:

```java
return new DefaultCoverageAnalysisProcessor(
        new FakeInputRepository(),
        contentStore,
        reports,
        gates,
        new LcovCoverageParser(),
        Clock.fixed(NOW, ZoneOffset.UTC));
```

Add assertion:

```java
@Then("a passed line coverage gate evaluation is persisted")
public void passedLineCoverageGateEvaluationIsPersisted() {
    assertEquals(1, reports.savedEvaluations.size());
    GateEvaluation evaluation = reports.savedEvaluations.getFirst();
    assertEquals("line-minimum", evaluation.gateName());
    assertEquals("passed", evaluation.status());
    assertEquals("line", evaluation.metric());
}
```

Update fake report repo:

```java
private static final class FakeReportRepository implements CoverageReportRepository {
    private CoverageReport savedReport;
    private List<GateEvaluation> savedEvaluations = List.of();

    @Override
    public void save(CoverageReport report) {
        savedReport = report;
    }

    @Override
    public void save(CoverageReport report, List<GateEvaluation> evaluations) {
        savedReport = report;
        savedEvaluations = List.copyOf(evaluations);
    }
}
```

Add fake gate repository:

```java
private static final class FakeGateConfigurationRepository implements GateConfigurationRepository {
    private List<GateConfiguration> gates = List.of();

    @Override
    public List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId) {
        return gates;
    }
}
```

- [ ] **Step 3: Update the feature scenario**

Modify the first scenario in `coverage-analysis.feature`:

```gherkin
Scenario: Coverage artifacts are merged and gates are evaluated
  Given an upload received message with LCOV coverage artifacts
  And object storage contains the LCOV shards
  And an active project coverage gate requiring 95 percent line coverage
  When the analysis worker polls once
  Then the coverage report is persisted with 3 covered lines out of 3
  And a passed line coverage gate evaluation is persisted
  And the analysis job is completed
  And the queue message is archived
```

- [ ] **Step 4: Run BDD and unit tests**

Run:

```bash
mvn -pl services/coverage-analysis test
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature
git commit -m "test: cover gate evaluation worker flow"
```

---

### Task 6: Update Service Documentation

**Files:**
- Modify: `docs/backend/services/04-coverage-analysis-service.md`
- Modify: `infra/supabase/README.md`

- [ ] **Step 1: Update coverage-analysis service status**

In `docs/backend/services/04-coverage-analysis-service.md`, update the milestone paragraph to say:

```markdown
The current queue-driven implementation consumes `upload.received` events from Supabase Postgres via PGMQ, claims the matching `analysis_jobs` row, downloads LCOV artifacts from Supabase Storage, merges shard coverage, persists project/file summaries, evaluates active project coverage gates, stores `gate_evaluations`, and archives or reschedules the queue message.
```

Add to Initial parser/evaluator support:

```markdown
Initial gate evaluator support:

- `project_coverage` gates for `line`, `branch`, `function`, and `statement`
- Blocking threshold miss -> `failed`
- Non-blocking threshold miss -> `warning`
- Disabled gates and gate types requiring missing inputs are skipped by report processing
```

Update the `gate_evaluations` database model section to match the actual schema:

```markdown
| `coverage_report_id` | uuid | Nullable FK to coverage report |
| `org_id` | uuid | Organization boundary |
| `branch` | text | Evaluated branch |
| `gate_name` | text | Gate config name at evaluation time |
| `gate_type` | text | Gate type at evaluation time |
| `metric` | text | Metric at evaluation time |
| `threshold` | numeric | Configured threshold |
| `actual` | numeric | Evaluated actual metric, nullable |
| `status` | text | `passed`, `failed`, or `warning` |
| `blocking` | boolean | Whether a threshold miss blocks |
| `details_json` | jsonb | Evaluation details |
| `evaluated_at` | timestamptz | Evaluation time |
```

- [ ] **Step 2: Update Supabase README**

In `infra/supabase/README.md`, update the worker summary to say:

```markdown
The worker downloads raw LCOV files from private Supabase Storage buckets using the service-role key, merges shards by repository file path, evaluates active project coverage gates, writes `coverage_reports`, `coverage_file_summaries`, and `gate_evaluations`, marks the upload processed, and emits `coverage.report.completed` plus `coverage.gates.evaluated` upload events when gates run.
```

- [ ] **Step 3: Commit**

```bash
git add docs/backend/services/04-coverage-analysis-service.md infra/supabase/README.md
git commit -m "docs: document coverage gate evaluation"
```

---

### Task 7: Final Verification

**Files:**
- No new edits unless verification fails.

- [ ] **Step 1: Run targeted service tests**

Run:

```bash
mvn -pl services/coverage-analysis test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run organization read tests as a regression check**

Run:

```bash
mvn -pl services/organization test
```

Expected: BUILD SUCCESS. This confirms the existing gate evaluation read model still accepts the rows produced by the new writer.

- [ ] **Step 3: Run the backend module set**

Run:

```bash
mvn -pl services/coverage-analysis,services/organization,services/upload test
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Review diff for security and data consistency**

Run:

```bash
git diff --stat
git diff -- services/coverage-analysis docs/backend/services/04-coverage-analysis-service.md infra/supabase/README.md
```

Check:

- No hardcoded secrets.
- SQL uses parameters for all variable values.
- Gate rows are inserted in the same transaction as reports.
- Old gate rows are deleted before old report rows during upload reprocessing.
- Unsupported gate types do not incorrectly fail builds.

- [ ] **Step 5: Commit any verification fixes**

If verification required fixes:

```bash
git add services/coverage-analysis docs/backend/services/04-coverage-analysis-service.md infra/supabase/README.md
git commit -m "fix: stabilize coverage gate evaluation"
```

If no fixes were needed, do not create an empty commit.

---

## Follow-On Plans

- Add service-token auth to Organization's internal effective-config route and replace direct gate DB reads with an HTTP effective-config client.
- Add `coverage_drop` evaluation once baseline selection rules are finalized.
- Add `patch_coverage` once PR diff ingestion writes changed-line maps.
- Add `component_coverage` once component/path selectors are represented in normalized coverage summaries.
- Add `mutation_score` once mutation reports are ingested.
- Add git check-run propagation so blocking failed gates update provider checks.

## Self-Review

- Spec coverage: the plan wires gate evaluation after report processing, loads existing gate config, and writes rows to the already-readable `gate_evaluations` table.
- Placeholder scan: all tasks name concrete files, commands, and expected results; unsupported gate types are deliberately out of scope and listed as follow-ons.
- Type consistency: `GateConfiguration`, `GateEvaluation`, `GateEvaluator`, `GateConfigurationRepository`, and `CoverageReportRepository.save(report, evaluations)` are used consistently across tasks.
