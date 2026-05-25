# PR Diff Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reliable PR diff coverage based on the true PR base/head diff, durable line-hit maps, patch coverage, newly missed lines, lost coverage lines, and API retrieval of line-hit/diff coverage data.

**Architecture:** Keep Coverage Analysis responsible for parsing coverage artifacts, storing normalized line-hit maps, and calculating coverage over a provider-neutral PR diff. Keep Git Integration responsible for provider credentials and exact base/head diff retrieval. Keep Organization/API Control Plane responsible for authenticated report reads, including PR diff coverage and per-file line-hit map retrieval.

**Tech Stack:** Java 25, Helidon 4 MP, JAX-RS, JSON-B, Java `HttpClient`, JUnit 5, Supabase Postgres in the `vericov` schema, GitHub REST compare/diff APIs through the existing Git Integration service boundary.

---

## Current State

- `LcovCoverageParser` reads LCOV `DA:<line>,<hits>` values but only retains executable and covered line sets.
- `CoverageReportMerger` merges file summaries but drops per-line hit counts before persistence.
- `JdbcCoverageReportRepository` persists `coverage_reports` and `coverage_file_summaries`; no line-hit table exists.
- Organization PR report reads return the latest whole-report coverage by `pull_request_number`; they do not include patch coverage or diff lines.
- Git Integration stores `git_pull_requests` with `base_sha` and `head_sha`, but has no internal endpoint or provider query path for exact base/head diff retrieval.
- Existing docs say Coverage Analysis should compute PR diffs, but the implementation has not reached that milestone.

## Completion Definition

The feature is complete when all of these are true:

- LCOV hit counts survive parsing, shard merge, report persistence, and readback.
- The database stores per-report, per-file, per-line hit counts without storing source text.
- Git Integration exposes an internal, service-authenticated query for the exact diff between a PR's recorded `base_sha` and `head_sha`.
- Coverage Analysis computes patch line coverage from changed executable head lines.
- Coverage Analysis identifies newly missed lines: changed executable head lines with zero head hits.
- Coverage Analysis identifies lost coverage lines: mapped lines that were covered in base coverage and are uncovered in head coverage.
- PR report reads can include patch coverage, newly missed lines, lost coverage lines, and line-level diff status.
- Commit report reads have a per-file line-hit map retrieval endpoint.
- Unit tests cover parser/merge behavior, diff parsing, patch coverage, lost coverage mapping, unavailable base coverage, and API response shape.
- `mvn -pl services/coverage-analysis,services/git-integration,services/organization test` passes.

## Non-Goals

- Do not implement patch coverage gate enforcement in this plan. This plan makes patch metrics available for the future gate evaluator.
- Do not persist source code or diff line text. Persist file paths, line numbers, change types, and hit counts only.
- Do not claim GitLab or Bitbucket diff retrieval is complete. Return `unsupported_provider` until provider adapters are added.
- Do not add a shared Java module unless implementation proves duplication is worse than the dependency cost.

## File Structure

Coverage Analysis:

- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/ParsedCoverageFile.java`: store immutable line-hit maps and derive executable/covered sets from hits.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/LcovCoverageParser.java`: retain every LCOV `DA` hit count.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReportMerger.java`: merge line hits across shards by summing hits per file/line.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReport.java`: carry line-hit entries alongside file summaries.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageLineHit.java`: immutable `filePath`, `lineNumber`, `hits` value object.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/CoverageReportRepository.java`: add read methods for report summaries and line hits needed by diff coverage.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java`: persist and retrieve `coverage_line_hits`.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/PullRequestDiff.java`: provider-neutral base/head diff.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/PullRequestDiffFile.java`: file-level diff metadata.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/PullRequestDiffLine.java`: line-level base/head mapping.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffLineType.java`: `ADDED`, `DELETED`, `CONTEXT`.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageCalculator.java`: patch coverage, newly missed lines, and lost coverage.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageReport.java`: calculated PR diff coverage result.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageFile.java`: file-level diff coverage result.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageLine.java`: line-level coverage/diff result.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/PullRequestDiffClient.java`: internal Git Integration client port.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultPrDiffCoverageProcessor.java`: orchestrates PR diff coverage after a head report is saved.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/PrDiffCoverageRepository.java`: persistence port for calculated PR diff coverage.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcPrDiffCoverageRepository.java`: Postgres persistence adapter.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`: invoke PR diff processor when `pullRequestNumber` is present.
- Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/git/InternalGitDiffHttpClient.java`: service-authenticated HTTP adapter for Git Integration.
- Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`: wire the new processor and HTTP client.

Git Integration:

- Create `services/git-integration/src/main/java/dev/vericov/git/application/GetPullRequestDiffCommand.java`: query command with tenant, org, repository, provider, PR number, base SHA, and head SHA.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitPullRequestDiffDetails.java`: response model for exact diff metadata.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitDiffFileDetails.java`: file-level diff response model.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitDiffLineDetails.java`: line-level diff response model.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderQueryService.java`: resolves integration credentials and fetches exact diffs.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/port/GitProviderQueryPort.java`: provider-neutral query port.
- Modify `services/git-integration/src/main/java/dev/vericov/git/application/port/GitActionRepository.java`: reuse `findPullRequest` for PR metadata validation.
- Modify `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubProviderClient.java`: implement exact compare diff retrieval or extract a dedicated query client if the file grows too large.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubUnifiedDiffParser.java`: parse provider patch hunks into line-number-only diff lines.
- Modify `services/git-integration/src/main/java/dev/vericov/git/api/InternalGitResource.java`: add `GET /internal/v1/git/repositories/{repository_id}/pull-requests/{number}/diff`.
- Create `services/git-integration/src/main/java/dev/vericov/git/api/PullRequestDiffHttpResponse.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/api/DiffFileHttpResponse.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/api/DiffLineHttpResponse.java`.
- Modify `services/git-integration/src/main/java/dev/vericov/git/config/GitIntegrationComponents.java`: wire query service and provider query port.

Organization/API Control Plane:

- Modify `services/organization/src/main/java/dev/vericov/organization/application/PullRequestCoverageReportDetails.java`: include optional diff coverage details.
- Create `services/organization/src/main/java/dev/vericov/organization/application/PullRequestDiffCoverageDetails.java`.
- Create `services/organization/src/main/java/dev/vericov/organization/application/DiffCoverageFileDetails.java`.
- Create `services/organization/src/main/java/dev/vericov/organization/application/DiffCoverageLineDetails.java`.
- Create `services/organization/src/main/java/dev/vericov/organization/application/CoverageLineHitMapDetails.java`.
- Create `services/organization/src/main/java/dev/vericov/organization/application/GetCoverageLineHitsQuery.java`.
- Modify `services/organization/src/main/java/dev/vericov/organization/application/port/OrganizationRepository.java`: add read methods for PR diff coverage and line-hit maps.
- Modify `services/organization/src/main/java/dev/vericov/organization/application/InMemoryOrganizationRepository.java`: support test fixtures for diff coverage and line hits.
- Modify `services/organization/src/main/java/dev/vericov/organization/adapter/jdbc/JdbcOrganizationRepository.java`: query new diff and line-hit tables.
- Modify `services/organization/src/main/java/dev/vericov/organization/application/OrganizationApplicationService.java`: authorize and return diff coverage/line hit maps.
- Modify `services/organization/src/main/java/dev/vericov/organization/api/PullRequestCoverageReportHttpResponse.java`: include `diff`.
- Create `services/organization/src/main/java/dev/vericov/organization/api/PullRequestDiffCoverageHttpResponse.java`.
- Create `services/organization/src/main/java/dev/vericov/organization/api/DiffCoverageFileHttpResponse.java`.
- Create `services/organization/src/main/java/dev/vericov/organization/api/DiffCoverageLineHttpResponse.java`.
- Create `services/organization/src/main/java/dev/vericov/organization/api/CoverageLineHitMapHttpResponse.java`.
- Modify `services/organization/src/main/java/dev/vericov/organization/api/RepositoryControlPlaneResource.java`: add `include_diff_lines` to PR report and add a line-hit map endpoint.

Schema and docs:

- Modify `infra/supabase/volumes/db/vericov.sql`: add line-hit and PR diff coverage tables, indexes, checks, and RLS enablement.
- Modify `docs/backend/services/04-coverage-analysis-service.md`: document line-hit storage and PR diff coverage processing.
- Modify `docs/backend/services/05-git-integration-service.md`: document the internal exact diff endpoint.
- Modify `docs/backend/services/02-api-control-plane-service.md`: document PR diff coverage response fields and line-hit map endpoint.

---

### Task 1: Preserve Line Hits Through LCOV Parsing and Merge

**Files:**
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/ParsedCoverageFile.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/LcovCoverageParser.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReportMerger.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReport.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageLineHit.java`
- Test: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/LcovCoverageParserTest.java`
- Test: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/CoverageReportMergerTest.java`

- [ ] **Step 1: Write parser test for LCOV hit preservation**

Add this test to `LcovCoverageParserTest`:

```java
@Test
void preservesLineHitCountsFromDaRecords() {
    ParsedCoverage parsed = parser.parse("coverage.lcov", """
            TN:
            SF:src/App.java
            DA:10,3
            DA:11,0
            end_of_record
            """.getBytes(StandardCharsets.UTF_8));

    ParsedCoverageFile file = parsed.files().getFirst();

    assertEquals(Map.of(10, 3L, 11, 0L), file.lineHits());
    assertEquals(Set.of(10, 11), file.executableLines());
    assertEquals(Set.of(10), file.coveredLines());
    assertEquals(2, file.line().total());
    assertEquals(1, file.line().covered());
}
```

- [ ] **Step 2: Run parser test and verify it fails**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=LcovCoverageParserTest#preservesLineHitCountsFromDaRecords test
```

Expected: FAIL because `ParsedCoverageFile#lineHits()` does not exist.

- [ ] **Step 3: Add immutable line-hit storage to `ParsedCoverageFile`**

Change the record to keep `Map<Integer, Long> lineHits` and derive line sets:

```java
public record ParsedCoverageFile(
        String filePath,
        Map<Integer, Long> lineHits,
        Set<String> branches,
        Set<String> coveredBranches,
        Set<String> functions,
        Set<String> coveredFunctions) {

    public ParsedCoverageFile {
        lineHits = Map.copyOf(lineHits == null ? Map.of() : lineHits);
        branches = Set.copyOf(branches == null ? Set.of() : branches);
        coveredBranches = Set.copyOf(coveredBranches == null ? Set.of() : coveredBranches);
        functions = Set.copyOf(functions == null ? Set.of() : functions);
        coveredFunctions = Set.copyOf(coveredFunctions == null ? Set.of() : coveredFunctions);
    }

    public Set<Integer> executableLines() {
        return lineHits.keySet();
    }

    public Set<Integer> coveredLines() {
        return lineHits.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
```

- [ ] **Step 4: Update `LcovCoverageParser` to store hits**

Replace `executableLines` and `coveredLines` in the parser accumulator with:

```java
private final Map<Integer, Long> lineHits = new HashMap<>();
```

Change `parseLineCoverage` to:

```java
private static void parseLineCoverage(FileAccumulator current, String value) {
    String[] parts = value.split(",");
    int lineNumber = Integer.parseInt(parts[0]);
    long hits = Long.parseLong(parts[1]);
    current.lineHits.merge(lineNumber, hits, Long::sum);
}
```

Create `ParsedCoverageFile` with `lineHits` instead of line sets.

- [ ] **Step 5: Run parser test and verify it passes**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=LcovCoverageParserTest#preservesLineHitCountsFromDaRecords test
```

Expected: PASS.

- [ ] **Step 6: Write merger test for shard hit summing**

Create `CoverageReportMergerTest`:

```java
@Test
void sumsLineHitsAcrossCoverageShards() {
    CoverageAnalysisInput input = new CoverageAnalysisInput(
            UPLOAD_ID,
            TENANT_ID,
            REPOSITORY_ID,
            "head123",
            "feature/diff",
            42,
            List.of());
    ParsedCoverage first = new ParsedCoverage(List.of(new ParsedCoverageFile(
            "src/App.java",
            Map.of(10, 1L, 11, 0L),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of())));
    ParsedCoverage second = new ParsedCoverage(List.of(new ParsedCoverageFile(
            "src/App.java",
            Map.of(10, 2L, 11, 4L),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of())));

    CoverageReport report = new CoverageReportMerger().merge(input, List.of(first, second), NOW);

    assertEquals(2, report.line().total());
    assertEquals(2, report.line().covered());
    assertEquals(List.of(
            new CoverageLineHit("src/App.java", 10, 3L),
            new CoverageLineHit("src/App.java", 11, 4L)),
            report.lineHits());
}
```

- [ ] **Step 7: Run merger test and verify it fails**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=CoverageReportMergerTest test
```

Expected: FAIL because `CoverageLineHit` and `CoverageReport#lineHits()` do not exist.

- [ ] **Step 8: Add `CoverageLineHit` and merge output**

Create:

```java
public record CoverageLineHit(String filePath, int lineNumber, long hits) {
    public CoverageLineHit {
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        if (hits < 0) {
            throw new IllegalArgumentException("hits must not be negative");
        }
    }
}
```

Add `List<CoverageLineHit> lineHits` to `CoverageReport` and build it from merged files sorted by `filePath`, then `lineNumber`.

- [ ] **Step 9: Run coverage-analysis unit tests**

Run:

```bash
mvn -pl services/coverage-analysis test
```

Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage
git commit -m "feat: preserve coverage line hits"
```

### Task 2: Persist and Retrieve Line-Hit Maps

**Files:**
- Modify: `infra/supabase/volumes/db/vericov.sql`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/CoverageReportRepository.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`

- [ ] **Step 1: Add schema for line hits**

Add this table after `coverage_file_summaries`:

```sql
CREATE TABLE IF NOT EXISTS vericov.coverage_line_hits (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    commit_sha text NOT NULL,
    file_path text NOT NULL,
    line_number integer NOT NULL CHECK (line_number > 0),
    hits bigint NOT NULL CHECK (hits >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (coverage_report_id, file_path, line_number)
);
```

Add indexes:

```sql
CREATE INDEX IF NOT EXISTS coverage_line_hits_report_file_idx
    ON vericov.coverage_line_hits (coverage_report_id, file_path, line_number);

CREATE INDEX IF NOT EXISTS coverage_line_hits_repository_commit_file_idx
    ON vericov.coverage_line_hits (repository_id, commit_sha, file_path, line_number);
```

Enable RLS:

```sql
ALTER TABLE vericov.coverage_line_hits ENABLE ROW LEVEL SECURITY;
```

- [ ] **Step 2: Extend repository port**

Add methods:

```java
Optional<CoverageReportSummary> findLatestByCommit(UUID repositoryId, String commitSha);

List<CoverageLineHit> findLineHits(UUID coverageReportId);

List<CoverageLineHit> findLineHits(UUID coverageReportId, String filePath);
```

If `CoverageReportSummary` does not exist in Coverage Analysis, create a small local record with report id, tenant id, repository id, upload id, commit sha, branch, PR number, and timestamps.

- [ ] **Step 3: Persist line hits in `JdbcCoverageReportRepository#save`**

Call `insertLineHits(connection, report)` after `insertFileSummaries(connection, report)`:

```java
private static void insertLineHits(java.sql.Connection connection, CoverageReport report) throws SQLException {
    try (var statement = connection.prepareStatement("""
            insert into vericov.coverage_line_hits (
                id, tenant_id, coverage_report_id, repository_id, commit_sha,
                file_path, line_number, hits, created_at
            )
            values (extensions.gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
        for (CoverageLineHit lineHit : report.lineHits()) {
            int index = 1;
            statement.setObject(index++, report.tenantId());
            statement.setObject(index++, report.reportId());
            statement.setObject(index++, report.repositoryId());
            statement.setString(index++, report.commitSha());
            statement.setString(index++, lineHit.filePath());
            statement.setInt(index++, lineHit.lineNumber());
            statement.setLong(index++, lineHit.hits());
            statement.setObject(index++, utc(report.generatedAt()));
            statement.addBatch();
        }
        statement.executeBatch();
    }
}
```

- [ ] **Step 4: Add line-hit read methods**

Read ordered line hits by report and optional file:

```java
select file_path, line_number, hits
from vericov.coverage_line_hits
where coverage_report_id = ?
  and (? is null or file_path = ?)
order by file_path, line_number
```

Map each row to `CoverageLineHit`.

- [ ] **Step 5: Extend processor test fake to assert saved hits**

In `downloadsLcovArtifactsAndPersistsMergedCoverageReport`, add:

```java
assertEquals(List.of(
        new CoverageLineHit("src/App.java", 1, 1L),
        new CoverageLineHit("src/App.java", 2, 7L),
        new CoverageLineHit("src/App.java", 3, 1L)),
        report.lineHits());
```

- [ ] **Step 6: Run targeted processor test**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=DefaultCoverageAnalysisProcessorTest#downloadsLcovArtifactsAndPersistsMergedCoverageReport test
```

Expected: PASS.

- [ ] **Step 7: Run coverage-analysis tests**

Run:

```bash
mvn -pl services/coverage-analysis test
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add infra/supabase/volumes/db/vericov.sql services/coverage-analysis
git commit -m "feat: persist coverage line hits"
```

### Task 3: Add Diff Coverage Domain and Calculator

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/PullRequestDiff.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/PullRequestDiffFile.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/PullRequestDiffLine.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffLineType.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageCalculator.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageReport.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageFile.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/diff/DiffCoverageLine.java`
- Test: `services/coverage-analysis/src/test/java/dev/vericov/analysis/diff/DiffCoverageCalculatorTest.java`

- [ ] **Step 1: Write patch coverage test**

Create `DiffCoverageCalculatorTest` with:

```java
@Test
void calculatesPatchCoverageFromChangedExecutableHeadLines() {
    PullRequestDiff diff = new PullRequestDiff("base123", "head456", List.of(new PullRequestDiffFile(
            "src/App.java",
            null,
            "modified",
            List.of(
                    new PullRequestDiffLine(null, 10, DiffLineType.ADDED),
                    new PullRequestDiffLine(null, 11, DiffLineType.ADDED),
                    new PullRequestDiffLine(20, 20, DiffLineType.CONTEXT)))));
    List<CoverageLineHit> headHits = List.of(
            new CoverageLineHit("src/App.java", 10, 4L),
            new CoverageLineHit("src/App.java", 11, 0L),
            new CoverageLineHit("src/App.java", 20, 0L));

    DiffCoverageReport report = new DiffCoverageCalculator().calculate(diff, headHits, List.of());

    assertEquals(2, report.patchLineTotal());
    assertEquals(1, report.patchLineCovered());
    assertEquals(new BigDecimal("50.00"), report.patchLinePercentage());
    assertEquals(List.of(11), report.files().getFirst().newlyMissedLines().stream()
            .map(DiffCoverageLine::headLineNumber)
            .toList());
}
```

- [ ] **Step 2: Write lost coverage test**

Add:

```java
@Test
void identifiesLostCoverageOnMappedLines() {
    PullRequestDiff diff = new PullRequestDiff("base123", "head456", List.of(new PullRequestDiffFile(
            "src/App.java",
            null,
            "modified",
            List.of(new PullRequestDiffLine(30, 31, DiffLineType.CONTEXT)))));
    List<CoverageLineHit> baseHits = List.of(new CoverageLineHit("src/App.java", 30, 7L));
    List<CoverageLineHit> headHits = List.of(new CoverageLineHit("src/App.java", 31, 0L));

    DiffCoverageReport report = new DiffCoverageCalculator().calculate(diff, headHits, baseHits);

    assertEquals(1, report.lostCoverageLineCount());
    DiffCoverageLine lost = report.files().getFirst().lostCoverageLines().getFirst();
    assertEquals(30, lost.baseLineNumber());
    assertEquals(31, lost.headLineNumber());
    assertEquals(7L, lost.baseHits());
    assertEquals(0L, lost.headHits());
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=DiffCoverageCalculatorTest test
```

Expected: FAIL because diff domain classes do not exist.

- [ ] **Step 4: Implement diff domain records**

Use immutable records. `PullRequestDiffLine` allows a null base line for added lines and a null head line for deleted lines:

```java
public record PullRequestDiffLine(Integer baseLineNumber, Integer headLineNumber, DiffLineType type) {
    public PullRequestDiffLine {
        Objects.requireNonNull(type, "type");
        if (baseLineNumber != null && baseLineNumber < 1) {
            throw new IllegalArgumentException("baseLineNumber must be positive");
        }
        if (headLineNumber != null && headLineNumber < 1) {
            throw new IllegalArgumentException("headLineNumber must be positive");
        }
    }
}
```

- [ ] **Step 5: Implement `DiffCoverageCalculator`**

Rules:

- Patch coverage denominator: `ADDED` lines with a head line number that exists in the head hit map.
- Patch coverage numerator: denominator lines where head hits are greater than zero.
- Newly missed lines: denominator lines where head hits equal zero.
- Lost coverage lines: `CONTEXT` lines with base and head line numbers where base hits are greater than zero and head hits equal zero.
- Deleted lines do not count toward patch coverage or lost coverage.
- Renames use `oldFilePath` for base hits and `filePath` for head hits.
- If denominator is zero, `patchLinePercentage` is `null`.

- [ ] **Step 6: Run calculator tests**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=DiffCoverageCalculatorTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/coverage-analysis/src/main/java/dev/vericov/analysis/diff services/coverage-analysis/src/test/java/dev/vericov/analysis/diff
git commit -m "feat: calculate pr diff coverage"
```

### Task 4: Add Exact Base/Head Diff Retrieval to Git Integration

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GetPullRequestDiffCommand.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitPullRequestDiffDetails.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitDiffFileDetails.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitDiffLineDetails.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderQueryService.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitProviderQueryPort.java`
- Modify: `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubProviderClient.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubUnifiedDiffParser.java`
- Modify: `services/git-integration/src/main/java/dev/vericov/git/api/InternalGitResource.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/PullRequestDiffHttpResponse.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/DiffFileHttpResponse.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/DiffLineHttpResponse.java`
- Test: `services/git-integration/src/test/java/dev/vericov/git/adapter/provider/github/GitHubUnifiedDiffParserTest.java`
- Test: `services/git-integration/src/test/java/dev/vericov/git/application/GitProviderQueryServiceTest.java`
- Test: `services/git-integration/src/test/java/dev/vericov/git/api/InternalGitResourceTest.java`

- [ ] **Step 1: Verify GitHub compare API contract before coding**

Use current official GitHub REST docs or Context7 to confirm:

- Endpoint path for comparing exact commits.
- Authentication scopes/permissions required for private repositories.
- Response shape for files, status, filename, previous filename, and patch.
- Large diff and binary file behavior.

Record the source URL in the implementation PR summary. Do not continue with guessed endpoint fields if docs differ.

- [ ] **Step 2: Write unified diff parser test**

Create parser test:

```java
@Test
void parsesHunkLineNumbersWithoutPersistingSourceText() {
    List<GitDiffLineDetails> lines = new GitHubUnifiedDiffParser().parse("""
            @@ -7,2 +7,3 @@
             unchanged
            -removed
            +added one
            +added two
            """);

    assertEquals(List.of(
            new GitDiffLineDetails(7, 7, "context"),
            new GitDiffLineDetails(8, null, "deleted"),
            new GitDiffLineDetails(null, 8, "added"),
            new GitDiffLineDetails(null, 9, "added")),
            lines);
}
```

- [ ] **Step 3: Run parser test and verify it fails**

Run:

```bash
mvn -pl services/git-integration -Dtest=GitHubUnifiedDiffParserTest test
```

Expected: FAIL because parser does not exist.

- [ ] **Step 4: Implement `GitHubUnifiedDiffParser`**

Parse only hunk headers and line prefixes:

- Hunk header regex: `^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$`
- `" "` increments both base and head line numbers and emits `context`.
- `"+"` increments head line number and emits `added`.
- `"-"` increments base line number and emits `deleted`.
- `"\\ No newline at end of file"` is ignored.
- Source text after the prefix is never stored.

- [ ] **Step 5: Write query service tests**

Add tests:

```java
@Test
void rejectsDiffWhenStoredPullRequestHeadDoesNotMatchRequestedHead() {
    GitProviderQueryService service = fixtureWithPullRequest("base123", "storedHead");

    GitIntegrationException exception = assertThrows(GitIntegrationException.class, () -> service.getPullRequestDiff(
            new GetPullRequestDiffCommand(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", 42, "base123", "requestedHead")));

    assertEquals("conflict", exception.code());
}

@Test
void fetchesDiffForRecordedBaseAndHead() {
    GitProviderQueryService service = fixtureWithPullRequest("base123", "head456");

    GitPullRequestDiffDetails diff = service.getPullRequestDiff(
            new GetPullRequestDiffCommand(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", 42, "base123", "head456"));

    assertEquals("base123", diff.baseSha());
    assertEquals("head456", diff.headSha());
    assertEquals("src/App.java", diff.files().getFirst().filePath());
}
```

- [ ] **Step 6: Implement query service**

Behavior:

- Validate tenant, org, repository, provider, PR number, base SHA, and head SHA.
- Load `git_pull_requests` through `GitActionRepository.findPullRequest`.
- Return `not_found` if no PR row exists.
- Return `conflict` if stored base/head does not match the requested base/head.
- Resolve integration with capability `git.repository_sync` unless a narrower `git.diff_read` capability is added in the same changeset.
- Lease credentials through `IntegrationConfigClient`.
- Call `GitProviderQueryPort.fetchPullRequestDiff`.

- [ ] **Step 7: Add provider query port implementation**

Either extend `GitHubProviderClient` or extract a dedicated query adapter. The provider query must call the exact base/head compare endpoint and return:

```java
new GitPullRequestDiffDetails(
        repositoryId,
        pullRequestNumber,
        baseSha,
        headSha,
        files);
```

For each file, map provider fields to:

- `filePath`
- `oldFilePath`
- `status`
- parsed `lines`

If GitHub indicates the diff is truncated or too large, throw `GitIntegrationException("validation_error", "Pull request diff is too large for exact coverage analysis")`; do not calculate partial patch coverage silently.

- [ ] **Step 8: Add internal resource route**

Add:

```java
@GET
@Path("/repositories/{repository_id}/pull-requests/{number}/diff")
public Response getPullRequestDiff(
        @HeaderParam("X-Vericov-Service-Name") String serviceName,
        @HeaderParam("X-Vericov-Service-Token") String serviceToken,
        @PathParam("repository_id") UUID repositoryId,
        @PathParam("number") int pullRequestNumber,
        @QueryParam("tenant_id") UUID tenantId,
        @QueryParam("org_id") UUID orgId,
        @QueryParam("provider") String providerKey,
        @QueryParam("base_sha") String baseSha,
        @QueryParam("head_sha") String headSha) {
    requireAuthorizedService(serviceName, serviceToken);
    GitPullRequestDiffDetails diff = queryService.getPullRequestDiff(new GetPullRequestDiffCommand(
            tenantId, orgId, repositoryId, providerKey, pullRequestNumber, baseSha, headSha));
    return Response.ok(new ApiResponse<>(PullRequestDiffHttpResponse.from(diff))).build();
}
```

- [ ] **Step 9: Run Git Integration tests**

Run:

```bash
mvn -pl services/git-integration test
```

Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add services/git-integration
git commit -m "feat: fetch exact pr diffs"
```

### Task 5: Persist PR Diff Coverage in Coverage Analysis

**Files:**
- Modify: `infra/supabase/volumes/db/vericov.sql`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultPrDiffCoverageProcessor.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/PullRequestDiffClient.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/PrDiffCoverageRepository.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcPrDiffCoverageRepository.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/git/InternalGitDiffHttpClient.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`
- Test: `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultPrDiffCoverageProcessorTest.java`

- [ ] **Step 1: Add schema for PR diff coverage**

Add tables:

```sql
CREATE TABLE IF NOT EXISTS vericov.pull_request_coverage_diffs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL UNIQUE REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    pull_request_number integer NOT NULL CHECK (pull_request_number > 0),
    provider_key text NOT NULL,
    base_sha text NOT NULL,
    head_sha text NOT NULL,
    status text NOT NULL CHECK (status IN ('complete', 'base_coverage_missing', 'unavailable')),
    patch_line_covered integer NOT NULL CHECK (patch_line_covered >= 0),
    patch_line_total integer NOT NULL CHECK (patch_line_total >= 0),
    newly_missed_line_count integer NOT NULL CHECK (newly_missed_line_count >= 0),
    lost_coverage_line_count integer NOT NULL CHECK (lost_coverage_line_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (patch_line_covered <= patch_line_total)
);

CREATE TABLE IF NOT EXISTS vericov.pull_request_coverage_diff_files (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    pr_diff_id uuid NOT NULL REFERENCES vericov.pull_request_coverage_diffs (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    file_path text NOT NULL,
    old_file_path text,
    change_status text NOT NULL,
    patch_line_covered integer NOT NULL CHECK (patch_line_covered >= 0),
    patch_line_total integer NOT NULL CHECK (patch_line_total >= 0),
    newly_missed_line_count integer NOT NULL CHECK (newly_missed_line_count >= 0),
    lost_coverage_line_count integer NOT NULL CHECK (lost_coverage_line_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (patch_line_covered <= patch_line_total)
);

CREATE TABLE IF NOT EXISTS vericov.pull_request_coverage_diff_lines (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    pr_diff_id uuid NOT NULL REFERENCES vericov.pull_request_coverage_diffs (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    file_path text NOT NULL,
    old_file_path text,
    base_line_number integer CHECK (base_line_number IS NULL OR base_line_number > 0),
    head_line_number integer CHECK (head_line_number IS NULL OR head_line_number > 0),
    change_type text NOT NULL CHECK (change_type IN ('added', 'deleted', 'context')),
    executable boolean NOT NULL DEFAULT false,
    base_hits bigint,
    head_hits bigint,
    newly_missed boolean NOT NULL DEFAULT false,
    lost_coverage boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
```

Add indexes:

```sql
CREATE INDEX IF NOT EXISTS pr_coverage_diffs_repository_pr_idx
    ON vericov.pull_request_coverage_diffs (repository_id, pull_request_number, created_at DESC);

CREATE INDEX IF NOT EXISTS pr_coverage_diff_files_diff_idx
    ON vericov.pull_request_coverage_diff_files (pr_diff_id, file_path);

CREATE INDEX IF NOT EXISTS pr_coverage_diff_lines_diff_file_idx
    ON vericov.pull_request_coverage_diff_lines (pr_diff_id, file_path, head_line_number);
```

Enable RLS for all three tables.

- [ ] **Step 2: Write processor test for patch coverage persistence**

Create:

```java
@Test
void fetchesExactPrDiffAndPersistsPatchCoverage() {
    FakePullRequestDiffClient diffClient = new FakePullRequestDiffClient(new PullRequestDiff(
            "base123",
            "head456",
            List.of(new PullRequestDiffFile("src/App.java", null, "modified", List.of(
                    new PullRequestDiffLine(null, 10, DiffLineType.ADDED),
                    new PullRequestDiffLine(null, 11, DiffLineType.ADDED))))));
    FakeCoverageReportRepository reports = FakeCoverageReportRepository.withBaseCoverage("base123", List.of());
    FakePrDiffCoverageRepository prDiffs = new FakePrDiffCoverageRepository();

    processor(diffClient, reports, prDiffs).process(inputForPr42("head456"), headReportWithHits(
            new CoverageLineHit("src/App.java", 10, 1L),
            new CoverageLineHit("src/App.java", 11, 0L)));

    DiffCoverageReport saved = prDiffs.savedReport();
    assertEquals("base123", saved.baseSha());
    assertEquals("head456", saved.headSha());
    assertEquals(2, saved.patchLineTotal());
    assertEquals(1, saved.patchLineCovered());
    assertEquals(1, saved.newlyMissedLineCount());
}
```

- [ ] **Step 3: Write processor test for base coverage missing**

Add:

```java
@Test
void calculatesPatchCoverageWhenBaseCoverageIsMissingButMarksLostCoverageUnavailable() {
    FakeCoverageReportRepository reports = FakeCoverageReportRepository.withoutBaseCoverage();

    processor(diffClientForBaseHead("base123", "head456"), reports, prDiffs).process(
            inputForPr42("head456"),
            headReportWithHits(new CoverageLineHit("src/App.java", 10, 0L)));

    assertEquals("base_coverage_missing", prDiffs.savedStatus());
    assertEquals(0, prDiffs.savedReport().lostCoverageLineCount());
}
```

- [ ] **Step 4: Run processor tests and verify they fail**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=DefaultPrDiffCoverageProcessorTest test
```

Expected: FAIL because processor and repository ports do not exist.

- [ ] **Step 5: Implement `DefaultPrDiffCoverageProcessor`**

Behavior:

- Return immediately when `CoverageAnalysisInput.pullRequestNumber()` is null.
- Fetch PR diff from Git Integration using tenant id, repository id, provider key `github` for the first implementation, PR number, base SHA, and head SHA.
- Require `diff.headSha()` to equal `CoverageReport.commitSha()`. If it does not, throw `IllegalStateException("PR diff head SHA does not match coverage report commit")`.
- Load head line hits from the just-created report object.
- Load base report by `diff.baseSha()` using `CoverageReportRepository.findLatestByCommit`.
- If base report exists, load base line hits; otherwise pass an empty base hit list and persist status `base_coverage_missing`.
- Calculate diff coverage with `DiffCoverageCalculator`.
- Save through `PrDiffCoverageRepository`.

- [ ] **Step 6: Implement JDBC PR diff persistence**

Use a single transaction:

- Delete existing `pull_request_coverage_diffs` for `coverage_report_id`.
- Insert parent diff row.
- Insert file rows.
- Insert line rows when the calculator returns them.

Do not store source text. Store only paths, line numbers, status, booleans, and hit counts.

- [ ] **Step 7: Wire into `DefaultCoverageAnalysisProcessor`**

Add constructor dependency:

```java
private final DefaultPrDiffCoverageProcessor prDiffCoverageProcessor;
```

After `reports.save(report);`, call:

```java
if (input.pullRequestNumber() != null) {
    prDiffCoverageProcessor.process(input, report);
}
```

If wiring needs a no-op for tests, create `PrDiffCoverageProcessor` port with a no-op implementation rather than checking for null.

- [ ] **Step 8: Implement internal Git HTTP client**

`InternalGitDiffHttpClient` should:

- Build the internal URL with query parameters for `tenant_id`, `org_id`, `provider`, `base_sha`, and `head_sha`.
- Send `X-Vericov-Service-Name: coverage-analysis`.
- Send `X-Vericov-Service-Token` from config.
- Parse the API envelope.
- Convert Git Integration response lines into Coverage Analysis `PullRequestDiff` records.
- Throw an `IllegalStateException` on non-2xx responses with the provider error code.

- [ ] **Step 9: Run coverage-analysis tests**

Run:

```bash
mvn -pl services/coverage-analysis test
```

Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add infra/supabase/volumes/db/vericov.sql services/coverage-analysis
git commit -m "feat: persist pr diff coverage"
```

### Task 6: Expose PR Diff Coverage and Line-Hit Map Reads

**Files:**
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/PullRequestCoverageReportDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/PullRequestDiffCoverageDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/DiffCoverageFileDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/DiffCoverageLineDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/CoverageLineHitMapDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/GetCoverageLineHitsQuery.java`
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/port/OrganizationRepository.java`
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/InMemoryOrganizationRepository.java`
- Modify: `services/organization/src/main/java/dev/vericov/organization/adapter/jdbc/JdbcOrganizationRepository.java`
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/OrganizationApplicationService.java`
- Modify: `services/organization/src/main/java/dev/vericov/organization/api/PullRequestCoverageReportHttpResponse.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/api/PullRequestDiffCoverageHttpResponse.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/api/DiffCoverageFileHttpResponse.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/api/DiffCoverageLineHttpResponse.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/api/CoverageLineHitMapHttpResponse.java`
- Modify: `services/organization/src/main/java/dev/vericov/organization/api/RepositoryControlPlaneResource.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/api/OrganizationResourceTest.java`

- [ ] **Step 1: Write service test for PR diff response**

Extend the existing report read test:

```java
PullRequestCoverageReportDetails pullRequestReport = fixture.service.getPullRequestCoverageReport(
        new GetPullRequestCoverageReportQuery(USER_ID, organization.id(), repository.id(), 42, true, 100, true));

assertNotNull(pullRequestReport.diffCoverage());
assertEquals(2, pullRequestReport.diffCoverage().patchLineTotal());
assertEquals(1, pullRequestReport.diffCoverage().patchLineCovered());
assertEquals(1, pullRequestReport.diffCoverage().newlyMissedLineCount());
assertEquals(1, pullRequestReport.diffCoverage().files().getFirst().lines().size());
```

Update `GetPullRequestCoverageReportQuery` to include `includeDiffLines`.

- [ ] **Step 2: Write service test for line-hit map retrieval**

Add:

```java
@Test
void returnsLineHitMapForAuthorizedRepositoryCommitAndFile() {
    CoverageLineHitMapDetails hits = fixture.service.getCoverageLineHits(new GetCoverageLineHitsQuery(
            USER_ID,
            organization.id(),
            repository.id(),
            "abc123",
            "src/App.java"));

    assertEquals("abc123", hits.commitSha());
    assertEquals(Map.of(10, 3L, 11, 0L), hits.files().get("src/App.java"));
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest test
```

Expected: FAIL because diff and line-hit details do not exist.

- [ ] **Step 4: Add application details and repository methods**

Add repository methods:

```java
Optional<PullRequestDiffCoverageDetails> findPullRequestDiffCoverage(UUID coverageReportId, boolean includeLines);

CoverageLineHitMapDetails findCoverageLineHits(UUID repositoryId, String commitSha, String filePath);
```

Validate `filePath` with the same safety rule used for artifact names plus allow `/` for repository-relative paths:

- Reject blank.
- Reject backslashes.
- Reject `..`.
- Require length between 1 and 1000.

- [ ] **Step 5: Implement JDBC queries**

For PR diff coverage:

```sql
select *
from vericov.pull_request_coverage_diffs
where coverage_report_id = ?
```

For files:

```sql
select *
from vericov.pull_request_coverage_diff_files
where pr_diff_id = ?
order by file_path
```

For lines when requested:

```sql
select *
from vericov.pull_request_coverage_diff_lines
where pr_diff_id = ?
order by file_path, coalesce(head_line_number, base_line_number), id
```

For line hits:

```sql
select h.file_path, h.line_number, h.hits, r.id as report_id, r.commit_sha
from vericov.coverage_reports r
join vericov.coverage_line_hits h on h.coverage_report_id = r.id
where r.repository_id = ?
  and r.commit_sha = ?
  and h.file_path = ?
  and r.status = 'complete'
order by h.line_number
```

- [ ] **Step 6: Add public API fields and endpoint**

Extend PR report:

```json
{
  "pull_request_number": 42,
  "head_sha": "head456",
  "report": {},
  "diff": {
    "base_sha": "base123",
    "head_sha": "head456",
    "status": "complete",
    "patch_line": {
      "covered": 1,
      "total": 2,
      "percentage": 50.0
    },
    "newly_missed_line_count": 1,
    "lost_coverage_line_count": 1,
    "files": []
  }
}
```

Add line-hit endpoint:

```java
@GET
@Path("/{org_id}/repositories/{repository_id}/commits/{sha}/line-hits")
public Response getCoverageLineHits(
        @HeaderParam("Authorization") String authorizationHeader,
        @HeaderParam("X-Vericov-User-Id") String userIdHeader,
        @PathParam("org_id") UUID organizationId,
        @PathParam("repository_id") UUID repositoryId,
        @PathParam("sha") String commitSha,
        @QueryParam("file_path") String filePath) {
    AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
    CoverageLineHitMapDetails hits = organizationService.getCoverageLineHits(new GetCoverageLineHitsQuery(
            user.userId(), organizationId, repositoryId, commitSha, filePath));
    return Response.ok(new ApiResponse<>(CoverageLineHitMapHttpResponse.from(hits))).build();
}
```

- [ ] **Step 7: Run organization tests**

Run:

```bash
mvn -pl services/organization test
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add services/organization
git commit -m "feat: expose pr diff coverage"
```

### Task 7: Update Service Contracts and BDD Coverage

**Files:**
- Modify: `docs/backend/services/04-coverage-analysis-service.md`
- Modify: `docs/backend/services/05-git-integration-service.md`
- Modify: `docs/backend/services/02-api-control-plane-service.md`
- Modify: `services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java`

- [ ] **Step 1: Add BDD scenario**

Add:

```gherkin
Scenario: Pull request uploads include patch coverage and newly missed lines
  Given an upload received message with PR LCOV coverage artifacts
  And object storage contains the PR LCOV shard
  And Git Integration has an exact base head diff for the pull request
  When the analysis worker polls once
  Then the coverage report is persisted with line hit maps
  And the pull request diff coverage is persisted with 1 covered patch line out of 2
  And the pull request diff coverage includes 1 newly missed line
```

- [ ] **Step 2: Run BDD test and verify it fails**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=RunAnalysisFeaturesTest test
```

Expected: FAIL until step definitions and fake diff client are wired.

- [ ] **Step 3: Implement BDD fixtures**

Extend `AnalysisSteps` test fixture with:

- A fake PR upload input with `pullRequestNumber = 42`.
- A fake Git diff client returning base `base123`, head `abc123`.
- An LCOV artifact with one covered changed line and one uncovered changed line.
- Assertions against fake `PrDiffCoverageRepository`.

- [ ] **Step 4: Update docs**

Document:

- `coverage_line_hits` table.
- `pull_request_coverage_diffs` tables.
- Internal Git diff endpoint.
- PR report `diff` object.
- Line-hit endpoint.
- Privacy rule: no source text is persisted by diff coverage.

- [ ] **Step 5: Run docs and BDD verification**

Run:

```bash
mvn -pl services/coverage-analysis -Dtest=RunAnalysisFeaturesTest test
mvn -pl services/coverage-analysis,services/git-integration,services/organization test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add docs/backend services/coverage-analysis/src/test
git commit -m "docs: document pr diff coverage"
```

### Task 8: Final Verification and Security Review

**Files:**
- Review: `infra/supabase/volumes/db/vericov.sql`
- Review: `services/coverage-analysis`
- Review: `services/git-integration`
- Review: `services/organization`
- Review: `docs/backend`

- [ ] **Step 1: Run focused module tests**

Run:

```bash
mvn -pl services/coverage-analysis,services/git-integration,services/organization test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run root test suite**

Run:

```bash
mvn test
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Review security-sensitive diff**

Run:

```bash
git diff -- infra/supabase/volumes/db/vericov.sql services/coverage-analysis services/git-integration services/organization docs/backend
```

Check:

- No source text is persisted in line-hit or diff tables.
- Internal Git diff endpoint requires `X-Vericov-Service-Name` and `X-Vericov-Service-Token`.
- Provider tokens are never logged or returned.
- Repository file paths reject `..` and backslashes at public API boundaries.
- PR diff calculation refuses mismatched stored/requested base/head SHAs.
- Large/truncated provider diffs do not produce partial patch coverage.
- RLS is enabled for all new tables and no grants are added for `anon` or `authenticated`.

- [ ] **Step 4: Check coverage for new core classes**

Run module tests with the repository's coverage command if configured. If no coverage plugin exists yet, record that unit coverage is verified through targeted tests but aggregate coverage reporting is not yet wired for Maven.

- [ ] **Step 5: Final commit**

```bash
git add infra/supabase/volumes/db/vericov.sql services/coverage-analysis services/git-integration services/organization docs/backend
git commit -m "feat: add pr diff coverage"
```

## Self-Review

- Spec coverage: true base/head diff is handled by the Git Integration internal query that validates stored PR base/head SHAs before provider retrieval. Patch coverage is handled by `DiffCoverageCalculator`. Newly missed lines are added-head executable lines with zero hits. Lost coverage lines are mapped context lines covered in base and uncovered in head. Line-hit retrieval is covered by `coverage_line_hits`, repository reads, and a public Control Plane endpoint.
- Placeholder scan: The plan avoids placeholder markers and names concrete files, commands, schemas, and test expectations.
- Type consistency: `CoverageLineHit`, `PullRequestDiff*`, `DiffCoverage*`, and HTTP response naming is consistent across tasks. Git Integration uses `Git*Details`; Coverage Analysis converts those into provider-neutral `PullRequestDiff*` records.
- Risk boundary: Patch gate enforcement, GitLab, Bitbucket, and source-bearing diff display are explicitly out of scope.
