# Upload Service

The Upload service is Vericov's durable ingestion boundary for coverage reports
and test-result artifacts. It authenticates upload callers, validates metadata,
stores raw artifacts, creates an analysis job, emits an upload event, and
returns a pollable upload id.

The fuller contract lives in
`docs/backend/services/03-upload-ingestion-service.md`.

## Why This Service Exists

CI upload clients need a fast, retry-safe endpoint. Coverage parsing and gate
evaluation should not happen inside the HTTP request. This service accepts the
input, makes it durable, and hands the slower work to Coverage Analysis.

## Current Architecture

```text
 CI / CLI / runner
       |
       v
+------+-------------------+
| Gateway / direct REST    |
+------+-------------------+
       |
       v
+------+-------------------+
| UploadResource           |
| create/status/artifacts  |
+------+-------------------+
       |
       v
+------+-------------------+
| UploadApplicationService |
+------+-------------------+
       |
       +----------------+----------------+-----------------+----------------+
       |                |                |                 |                |
       v                v                v                 v                v
+------+-----+  +-------+--------+ +-----+--------+ +------+-------+ +------+-------+
| Auth port  |  | UploadRepo     | | Artifact     | | WorkQueue    | | Event        |
| API key /  |  | JDBC or memory | | Storage      | | analysis job | | Publisher    |
| runner JWT |  +----------------+ +--------------+ +--------------+ +--------------+
```

## Where It Is Called From

```text
vericov CLI in CI
  -> POST /api/v1/uploads with repo API key and artifacts
  -> upload service stores raw artifacts
  -> upload service queues analysis job and emits upload.received
  -> CLI polls GET /api/v1/uploads/{upload_id}

Enterprise/self-hosted runner
  -> POST /api/v1/uploads/auth/runner-token
  -> receives short-lived runner upload token
  -> uploads generated artifacts for the assigned task

Coverage analysis worker
  -> reads queued upload.received event
  -> loads artifact metadata from database/storage
  -> parses reports asynchronously
```

## Data Model

```text
uploads
  id, tenant_id, repository_id, commit_sha, branch, pull_request_number
  ci_provider, ci_build_id, ci_build_url, flags, component, package
  status, idempotency_key, api_key_id, accepted_at, analysis_job_id
  failure_code, failure_message, created_at, updated_at

upload_artifacts
  id, tenant_id, upload_id, name, kind, format, content_type
  size_bytes, sha256, storage_bucket, storage_path, status, created_at

upload_events
  id, tenant_id, upload_id, event_type, payload_json, created_at

analysis_jobs
  id, tenant_id, repository_id, upload_id, commit_sha
  job_type, status, priority, payload_json, available_at, attempt_count

repository_api_keys
  repository-owned table read by upload auth adapters; secrets are hashed
```

Artifact object paths are scoped by tenant and upload:

```text
{tenant_id}/{upload_id}/{artifact_kind}/{artifact_name}
```

## APIs

Public:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/uploads` | Accept upload metadata plus Base64 artifact contents |
| `GET` | `/api/v1/uploads/{upload_id}` | Poll upload status |
| `GET` | `/api/v1/uploads/{upload_id}/artifacts` | List stored artifact metadata |
| `POST` | `/api/v1/uploads/auth/runner-token` | Exchange allowed identity for runner upload token |

Accepted upload response:

```json
{
  "data": {
    "upload_id": "84cb6d3c-312f-43af-a3fc-5964b2d27626",
    "status": "queued",
    "poll_url": "/api/v1/uploads/84cb6d3c-312f-43af-a3fc-5964b2d27626",
    "analysis_job_id": "fb0e1e5d-55d7-4f74-9303-7a93400d53a1"
  }
}
```

Authentication modes:

| Caller | Credential |
| --- | --- |
| CI job | Repo-scoped Vericov API key |
| Runner | Short-lived runner upload JWT |
| Gateway/manual | Service JWT from veriapi/customer gateway |
| Trusted CI | GitHub Actions OIDC after trust config exists |

## Source Map

```text
api/
  JAX-RS resource, request/response records, JSON error mapper

application/
  Upload workflow, idempotency, side effects, events, in-memory repository

application/port/
  Auth, storage, repository, event publisher, and queue interfaces

domain/
  Upload commands, artifact inputs, principal, status, artifact kind

adapter/auth/
  Repo API key hashing/auth, runner token issuer, JWT/OIDC support

adapter/storage/
  Supabase object storage client and artifact storage implementation

config/
  Development CDI wiring and local in-memory adapters
```

## Tests

```text
src/test/resources/features/upload/upload-ingestion.feature
  BDD coverage for authorized upload, idempotency, scope/branch rejection,
  unsafe artifact rejection, artifact metadata, and runner upload tokens

src/test/java/dev/vericov/upload/api
  Resource behavior and error mapping

src/test/java/dev/vericov/upload/application
  Upload workflow behavior

src/test/java/dev/vericov/upload/adapter
  API key hashing and storage adapter behavior
```

Run this service only:

```bash
mvn -pl services/upload test
```
