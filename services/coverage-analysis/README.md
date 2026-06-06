# Coverage Analysis Service

The Coverage Analysis service is Vericov's internal worker for turning raw
coverage and test-result uploads into normalized reports, gate evaluations,
diff coverage, and read models consumed by the control-plane service.

The fuller contract lives in
`docs/backend/services/04-coverage-analysis-service.md`.

## Why This Service Exists

Upload ingestion must return quickly and be safe to retry. Coverage parsing,
report merging, test-result parsing, gate evaluation, and pull-request diff
coverage are slower and can fail independently. This service isolates that work
behind a durable queue and keeps coverage semantics out of the public API
control plane.

## Current Architecture

```text
                 PGMQ message: upload.received
                            |
                            v
+-------------------+  pollOnce  +------------------------+
| AnalysisJobQueue  | ---------> | UploadAnalysisEvent    |
| PGMQ/JDBC or fake |            | Handler                |
+-------------------+            +-----------+------------+
                                             |
                                             v
                                  +----------+------------+
                                  | DefaultCoverage       |
                                  | AnalysisProcessor     |
                                  +----------+------------+
                                             |
         +-----------------------------------+-----------------------------------+
         |                  |                |                 |                 |
         v                  v                v                 v                 v
+--------+-------+ +--------+-------+ +------+--------+ +------+--------+ +------+------+
| ArtifactContent| | CoverageParser | | TestResult    | | GateEvaluator | | Diff PR     |
| Store          | | Registry       | | ParserRegistry| |               | | Processor   |
+--------+-------+ +--------+-------+ +------+--------+ +------+--------+ +------+------+
         |                  |                |                 |                 |
         v                  v                v                 v                 v
 Supabase Storage     LCOV/XML/gcov     JUnit XML       gate rows         Git diff client
```

## Where It Is Called From

```text
CI client
  -> upload service stores raw artifacts
  -> upload service enqueues upload.received
  -> coverage-analysis polls coverage_analysis_jobs
  -> coverage-analysis stores coverage_reports, line hits, test_runs, gates
  -> control-plane service serves report reads and dashboards

Pull request upload
  -> coverage-analysis requests true provider diff from git-integration
  -> compares base/head line-hit maps
  -> stores pr_diff_coverage for UI and policy decisions
```

The service has no public product API. It is an internal worker plus internal
control API surface.

## Data Model

Key domain objects:

| Concept | What It Represents |
| --- | --- |
| `CoverageAnalysisInput` | Upload metadata and artifact locations to process |
| `CoverageReport` | Merged commit or PR-head coverage summary |
| `CoverageLineHit` | Per-file executable line hit data |
| `TestRun` | Aggregated JUnit/test-result summary |
| `GateConfiguration` | Repository-level threshold rule |
| `GateEvaluation` | Result of applying a gate to a report |
| `DiffCoverageReport` | PR patch coverage, missed lines, and lost coverage |
| `QueuedAnalysisMessage` | Durable queue message wrapping an event |

Persistence shape:

```text
analysis_jobs
  id, upload_id, repository_id, commit_sha, status, attempts, available_at

coverage_reports
  id, upload_id, tenant_id, repository_id, commit_sha, branch, pr_number
  line_covered, line_total, branch_covered, branch_total
  function_covered, function_total, statement_covered, statement_total
  normalized_storage_bucket, normalized_storage_path

coverage_file_summaries
  report_id, file_path, line/branch/function/statement rollups

coverage_line_hits
  report_id, file_path, line_number, hits

test_runs
  upload_id, artifact_id, suite_name, total, passed, failed, skipped

gate_evaluations
  report_id, gate_id, status, metric, actual, expected

pr_diff_coverage
  repository_id, pull_request_number, base_sha, head_sha, status, rollups
```

Normalized coverage maps are stored separately in Supabase Storage so the
database can keep fast summaries while detailed line maps stay compressed.

## APIs And Events

Implemented worker path:

```text
coverage_analysis_jobs
  -> upload.received
  -> processor success: archive message, complete analysis_jobs row
  -> retryable failure: record failure, reschedule message
  -> unsupported event: move to coverage_analysis_dead_letters
```

Documented internal endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/coverage-analysis/jobs/{job_id}/lease` | Lease an analysis job |
| `POST` | `/internal/v1/coverage-analysis/jobs/{job_id}/complete` | Mark analysis complete |
| `POST` | `/internal/v1/coverage-analysis/jobs/{job_id}/fail` | Mark analysis failed |
| `POST` | `/internal/v1/coverage-analysis/repositories/{repository_id}/commits/{sha}/analyze` | Trigger commit analysis |
| `POST` | `/internal/v1/coverage-analysis/repositories/{repository_id}/pull-requests/{number}/analyze` | Trigger PR diff analysis |
| `POST` | `/internal/v1/coverage-analysis/gates/evaluate` | Evaluate gates |

## Source Map

```text
application/
  Worker loop, event handler, coverage processor, PR diff processor

coverage/
  LCOV, JaCoCo, Cobertura, Clover, Go cover, gcov parsers and merger

testresults/
  JUnit parser and secure XML reader

gates/
  Gate model and evaluator

gaps/
  Coverage gap extraction and risk scoring

diff/
  Provider diff model and diff coverage calculator

adapter/jdbc/
  PGMQ, report, line-hit, gate, input, and test-run persistence

adapter/storage/
  Supabase artifact and normalized coverage storage
```

## Tests

```text
src/test/resources/features/analysis/coverage-analysis.feature
  Queue-driven BDD scenarios for success, JUnit-only uploads, retries,
  mixed coverage formats, busy/completed jobs, exhausted retry dead letters,
  and unsupported events

src/test/java/dev/vericov/analysis/coverage
  Parser and merger unit tests

src/test/java/dev/vericov/analysis/application
  Worker, event handler, report processor, and PR diff processor tests

src/test/java/dev/vericov/analysis/adapter
  HTTP/storage/JDBC adapter tests where local fakes are possible
```

Run this service only:

```bash
mvn -pl services/coverage-analysis test
```
