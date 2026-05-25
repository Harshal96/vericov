# Coverage Normalized Storage Technical Design

**Date:** 2026-05-25

**Status:** Proposed

**Owner:** Coverage analysis service

## Problem

Coverage analysis currently persists relational coverage summaries and per-line hit rows, but it does not create the normalized coverage map object described by the product and backend docs. The database schema already includes `coverage_reports.normalized_storage_bucket` and `coverage_reports.normalized_storage_path`, but the coverage-analysis Java model and JDBC insert path do not populate those columns. Supabase bootstrap SQL also does not create the documented `coverage-normalized` storage bucket.

This leaves downstream consumers without a durable canonical coverage artifact for larger map retrieval, replay, export, or later agent workflows.

## Goals

- After every successful coverage analysis, persist a canonical normalized coverage map to object storage.
- Write `coverage_reports.normalized_storage_bucket` and `coverage_reports.normalized_storage_path` for the stored map.
- Keep relational summaries and `coverage_line_hits` as query-optimized projections.
- Make normalized storage failures visible by failing the analysis attempt so the worker retry path can recover.
- Add tests that prove both the object write and database metadata persistence happen.

## Non-Goals

- Do not replace `coverage_file_summaries` or `coverage_line_hits`.
- Do not add public download APIs for normalized maps in this feature.
- Do not backfill historical reports in the first implementation.
- Do not introduce a new external storage backend beyond the existing Supabase Storage HTTP adapter shape.
- Do not store raw parser-specific coverage payloads in the normalized object.

## Current State

- `infra/supabase/volumes/db/vericov.sql` defines nullable `normalized_storage_bucket` and `normalized_storage_path` on `vericov.coverage_reports`.
- `docs/backend/services/03-upload-ingestion-service.md` documents a `coverage-normalized` bucket.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReport.java` has no normalized storage fields.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/ArtifactContentStore.java` supports reads only.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/storage/HttpSupabaseArtifactContentStore.java` downloads raw artifacts only.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java` inserts summaries and line hits, but omits the normalized storage columns.
- `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java` parses raw artifacts, merges coverage, evaluates gates, saves the report, then computes PR diff coverage.

## Proposed Architecture

Add a small write-side normalized coverage storage component inside `coverage-analysis`. The processor will parse and merge coverage exactly as it does today, serialize the merged `CoverageReport` into a canonical JSON map, gzip it, upload it to the private `coverage-normalized` bucket, attach the resulting storage location to the immutable `CoverageReport`, and then save the report plus gate evaluations.

The relational database remains the source for common queries. The normalized object becomes the durable full-fidelity canonical artifact.

Processing order:

1. Load upload metadata.
2. Download raw coverage artifacts.
3. Parse each artifact through `CoverageParserRegistry`.
4. Merge into `CoverageReport`.
5. Serialize and upload normalized coverage map.
6. Create a new `CoverageReport` instance with normalized storage location.
7. Evaluate gates against the enriched report.
8. Save report, file summaries, line hits, gate evaluations, upload status, and events in the existing transaction.
9. Run PR diff coverage.

The object upload intentionally happens before the database transaction. If upload fails, no complete report is inserted. If the DB transaction fails after upload succeeds, the retry will attempt to write the same deterministic object path. The storage adapter should therefore support idempotent upsert for normalized artifacts.

## Storage Bucket

Create a private Supabase Storage bucket:

- Bucket id: `coverage-normalized`
- Bucket name: `coverage-normalized`
- Public: `false`
- File size limit: `104857600` bytes, matching raw coverage
- Allowed MIME types:
  - `application/gzip`
  - `application/json`
  - `application/octet-stream`

Update `infra/supabase/volumes/db/vericov.sql` so the bootstrap creates and configures `coverage-normalized` alongside `coverage-raw`, `test-results-raw`, and `metadata-raw`.

## Object Path

Use a deterministic tenant/upload-scoped path:

```text
{tenant_id}/{upload_id}/coverage-normalized/coverage-map.json.gz
```

Example:

```text
0f4f478a-3fc0-45c4-b274-43a0e18850cf/03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6/coverage-normalized/coverage-map.json.gz
```

The path is stable across retries for the same upload. If object upload succeeds and the database transaction later fails, a retry overwrites the same normalized object path with the regenerated report payload. This avoids accumulating one orphaned object per failed attempt while still keeping paths naturally scoped by tenant and upload.

## Normalized Map Format

Version the object format from day one.

Content type:

```text
application/gzip
```

Uncompressed JSON shape:

```json
{
  "schema_version": 1,
  "report": {
    "id": "7a36b5bc-6bd2-44a2-bc8f-c886b809cf4d",
    "upload_id": "03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6",
    "tenant_id": "0f4f478a-3fc0-45c4-b274-43a0e18850cf",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "commit_sha": "abc123",
    "branch": "main",
    "pull_request_number": 42,
    "generated_at": "2026-05-23T12:00:00Z"
  },
  "totals": {
    "line": { "covered": 3, "total": 3 },
    "branch": { "covered": 1, "total": 2 },
    "function": { "covered": 1, "total": 1 },
    "statement": { "covered": 3, "total": 3 }
  },
  "files": [
    {
      "path": "src/App.java",
      "metrics": {
        "line": { "covered": 3, "total": 3 },
        "branch": { "covered": 1, "total": 2 },
        "function": { "covered": 1, "total": 1 },
        "statement": { "covered": 3, "total": 3 }
      },
      "line_hits": [
        { "line": 1, "hits": 1 },
        { "line": 2, "hits": 7 },
        { "line": 3, "hits": 1 }
      ]
    }
  ]
}
```

Ordering requirements:

- `files` sorted by `path`.
- `line_hits` sorted by `line`.
- JSON keys emitted in stable order.

Security and privacy:

- Include only normalized coverage metadata and line-hit counts already derived by the service.
- Do not include raw uploaded report content.
- Do not include service role keys, storage URLs, request headers, CI environment values, or parser diagnostics.

## Application Changes

### Domain Model

Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReport.java`:

- Add `String normalizedStorageBucket`.
- Add `String normalizedStoragePath`.
- Add a method that returns a new record with normalized storage attached:

```java
public CoverageReport withNormalizedStorage(String bucket, String path) {
    return new CoverageReport(
            reportId,
            uploadId,
            tenantId,
            repositoryId,
            commitSha,
            branchName,
            pullRequestNumber,
            line,
            branch,
            function,
            statement,
            files,
            lineHits,
            bucket,
            path,
            generatedAt);
}
```

The exact constructor parameter order should keep `generatedAt` last or migrate call sites carefully in one task. Existing tests should assert the new fields.

### Storage Port

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/NormalizedCoverageStore.java`:

```java
package dev.vericov.analysis.application.port;

import dev.vericov.analysis.coverage.CoverageReport;

public interface NormalizedCoverageStore {
    NormalizedCoverageLocation store(CoverageReport report);
}
```

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/NormalizedCoverageLocation.java`:

```java
package dev.vericov.analysis.application.port;

import java.util.Objects;

public record NormalizedCoverageLocation(String bucket, String path) {
    public NormalizedCoverageLocation {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(path, "path");
        if (bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
    }
}
```

Keep this separate from `ArtifactContentStore` so raw artifact reads and normalized artifact writes do not become a vague bidirectional abstraction.

### Serialization

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/NormalizedCoverageMapSerializer.java`.

Responsibilities:

- Build the versioned JSON shape.
- Use Jakarta JSON APIs already available through Helidon rather than adding a new JSON dependency.
- Gzip the UTF-8 JSON bytes with `GZIPOutputStream`.
- Return immutable byte arrays.

Test this class directly by decompressing bytes and comparing decoded JSON values.

### Supabase Storage Adapter

Create `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/storage/SupabaseNormalizedCoverageStore.java`.

Responsibilities:

- Accept storage base URI, service role key, bucket name, and serializer.
- Generate the object path.
- Upload gzip bytes to Supabase Storage.
- Return `NormalizedCoverageLocation`.

Implementation detail:

- Reuse the URI encoding approach from `HttpSupabaseArtifactContentStore`.
- Use `POST /storage/v1/object/{bucket}/{path}`.
- Send `Content-Type: application/gzip`.
- Send `cache-control: 3600`.
- Send `x-upsert: true` for retry idempotency.
- Throw `IllegalStateException` for non-2xx responses.

The existing upload service has a similar `HttpSupabaseObjectStorageClient`, but coverage-analysis should not depend on the upload module. A small local adapter is acceptable.

### Processor

Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`:

- Add a `NormalizedCoverageStore` dependency.
- Preserve current constructors by adding overloads that use `NormalizedCoverageStore.noop()` only if needed for tests, or update tests explicitly with a fake store.
- After `CoverageReport report = merger.merge(...)`, call `normalizedCoverageStore.store(report)`.
- Replace `report` with `report.withNormalizedStorage(location.bucket(), location.path())`.
- Evaluate gates and persist using the enriched report.

Preferred behavior:

```java
CoverageReport report = merger.merge(input, parsedCoverages, clock.instant());
NormalizedCoverageLocation location = normalizedCoverageStore.store(report);
CoverageReport reportWithStorage = report.withNormalizedStorage(location.bucket(), location.path());
List<GateEvaluation> evaluations = gateEvaluator.evaluate(
        reportWithStorage,
        gates.listActiveForRepository(reportWithStorage.tenantId(), reportWithStorage.repositoryId()),
        reportWithStorage.generatedAt());
reports.save(reportWithStorage, evaluations);
prDiffCoverageProcessor.process(input, reportWithStorage);
```

### JDBC Persistence

Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java`:

- Add `normalized_storage_bucket` and `normalized_storage_path` to the insert column list.
- Bind both values from `CoverageReport`.
- Use `setString` for both fields. They should be non-null after processor enrichment, but nullable schema keeps compatibility for older rows and focused tests.

### Configuration

Modify `services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java`:

- Read `VERICOV_NORMALIZED_COVERAGE_BUCKET`, defaulting to `coverage-normalized`.
- Construct `SupabaseNormalizedCoverageStore` with the same Supabase storage base URI and service role key as raw artifact reads.
- Pass the store into `DefaultCoverageAnalysisProcessor`.

Document the new env var in `infra/supabase/README.md` and `docs/backend/services/04-coverage-analysis-service.md`.

## Testing Plan

### Unit Tests

Add `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/NormalizedCoverageMapSerializerTest.java`:

- Serializes a report with two files and verifies gzip output is valid.
- Verifies `schema_version`, report metadata, totals, file metrics, and line hits.
- Verifies file and line ordering is stable.

Update `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`:

- Fake normalized store records the report it received and returns `coverage-normalized/{tenant}/{upload}/coverage-normalized/coverage-map.json.gz`.
- Existing happy-path tests assert:
  - normalized store was called once
  - saved report has `normalizedStorageBucket()`
  - saved report has `normalizedStoragePath()`
- Add a failure test where normalized store throws and assert:
  - repository save is not called
  - exception propagates

Add `services/coverage-analysis/src/test/java/dev/vericov/analysis/adapter/storage/SupabaseNormalizedCoverageStoreIntegrationTest.java`:

- Starts local `HttpServer`.
- Verifies method, encoded path, auth headers, `Content-Type`, and `x-upsert: true`.
- Decompresses request body and verifies JSON contains the report id.
- Verifies non-2xx upload response raises `IllegalStateException`.

### JDBC Tests

There is no current focused `JdbcCoverageReportRepositoryTest`. Add one if the test harness already has a lightweight database pattern available; otherwise, cover the insert binding through an adapter-level test using a fake `DataSource` only if that remains simpler than spinning Postgres.

Minimum assertion:

- `insertCoverageReport` writes `normalized_storage_bucket` and `normalized_storage_path`.

If no practical JDBC test harness exists, add a precise processor-level test plus document the manual SQL verification command. This is less ideal but still improves coverage where the current suite has no JDBC repository test.

### BDD

Update `services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature`:

- Extend the successful LCOV scenario with: `And a normalized coverage map is stored`.

Update `services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java`:

- Add fake normalized store.
- Assert one stored normalized object and saved report location.

### Verification Commands

Run:

```bash
mvn -pl services/coverage-analysis test
```

If infra SQL is changed and local Supabase is available, also verify bootstrap bucket creation with:

```bash
docker compose -f infra/supabase/docker-compose.yml up -d db storage
```

Then inspect `storage.buckets` for `coverage-normalized`.

## Rollout

1. Ship schema/bootstrap support for `coverage-normalized`.
2. Ship coverage-analysis object writing and report metadata persistence.
3. Keep old rows readable because normalized storage fields remain nullable.
4. Monitor analysis job retries for storage upload failures.
5. Consider a follow-up backfill worker for historical reports once consumers need it.

## Risks And Mitigations

- **Object upload succeeds but DB transaction fails:** Use deterministic upload-scoped paths and `x-upsert: true`; tolerate a stale object for that upload until retry succeeds or a later cleanup process removes abandoned uploads.
- **Large coverage reports consume memory during serialization:** Current parsing and merging are already in-memory. This feature should not make that worse by more than one gzip byte array. Streaming can be a follow-up if large repos hit memory limits.
- **Normalized JSON schema changes:** Include `schema_version` and keep schema additions backward-compatible.
- **Retry regenerates report ids:** Keep the object path upload-scoped and use `x-upsert: true` so retries replace the prior attempt's normalized payload.
- **Sensitive data leak:** Serializer must only use fields from `CoverageReport`; no raw artifact content, environment, headers, or parser exception text.

## Implementation Tasks

1. Add `coverage-normalized` bucket bootstrap and docs.
2. Add normalized storage fields to `CoverageReport` and update call sites.
3. Add `NormalizedCoverageLocation` and `NormalizedCoverageStore`.
4. Add `NormalizedCoverageMapSerializer` and unit tests.
5. Add `SupabaseNormalizedCoverageStore` and HTTP integration tests.
6. Wire normalized storage into `DefaultCoverageAnalysisProcessor`.
7. Persist normalized storage fields in `JdbcCoverageReportRepository`.
8. Update BDD scenario and steps.
9. Run `mvn -pl services/coverage-analysis test`.

## Open Questions

- Should object paths include `commit_sha` for easier storage browsing, or remain upload/report scoped to avoid path sanitization concerns?
- Should the first implementation store plain JSON or gzip only? This design prefers gzip because docs call these normalized maps large, but plain JSON is easier to inspect locally.
- Do we want to expose a future internal endpoint that streams the normalized object by `coverage_report_id`, or will consumers read via service-role storage access?
