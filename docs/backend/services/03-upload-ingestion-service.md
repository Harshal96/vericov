# Upload / Ingestion Service Contract

Status: Draft for review
Runtime: Helidon 4 on Java 25+
Public base path: `/api/v1/uploads`
Internal base path: `/internal/v1/uploads`
OpenAPI: `/openapi`

## Purpose

The Upload / Ingestion Service receives coverage reports and test result artifacts from CI, validates upload identity, stores raw artifacts in Supabase Storage, creates an internal processing event, and immediately returns a unique upload identifier that clients can poll.

This service does not compute coverage. It is a durable, idempotent ingestion boundary.

## Authentication

Upload authentication is separate from human login.

| Caller | Credential | Allowed actions |
| --- | --- | --- |
| Web app / local authenticated user | Supabase Auth JWT | Create manual uploads only for repositories where the user has permission |
| CI job | Repo-scoped Vericov API key, sent as `Authorization: Bearer vc_repo_...` | Create uploads and poll upload status within key scope |
| Trusted CI provider | GitHub Actions OIDC token | Tokenless upload after repository trust is configured |
| Enterprise runner | Short-lived Vericov runner upload JWT | Upload runner-produced artifacts for assigned tasks |
| Internal services | Service JWT or mTLS-bound token | Internal status and parser updates |

Repo-scoped API keys are created through the API / Control Plane Service and stored hashed in `repository_api_keys`. The Upload / Ingestion Service validates key hash, repository scope, allowed branches, expiration, revocation status, and requested scopes before accepting an upload. GitHub Actions OIDC tokens are verified against GitHub's JWKS before matching `repository_ci_trusts`.

Minimum CI configuration:

```bash
VERICOV_API_KEY=vc_repo_...
vericov upload --coverage coverage/lcov.info --test-results junit.xml
```

## Public Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/uploads` | Directly upload coverage/test artifacts and enqueue processing |
| `POST` | `/api/v1/uploads/auth/runner-token` | Exchange a repo API key or trusted CI identity for a short-lived runner upload JWT |
| `GET` | `/api/v1/uploads/{upload_id}` | Get upload status |
| `GET` | `/api/v1/uploads/{upload_id}/artifacts` | List uploaded artifact metadata |

## Direct Upload Behavior

`POST /api/v1/uploads` is a complete upload request. The client sends metadata and artifact bytes in one call. If the request is accepted, Vericov has enough durable input to process asynchronously and returns an `upload_id` immediately.

Rules:

- The request is authenticated by repository API key, Supabase Auth JWT, runner upload JWT, GitHub Actions OIDC identity, or service JWT.
- Polling upload status and artifact metadata requires `uploads:read` for the upload repository.
- The service validates repository, branch, commit, artifact metadata, request size, and key scope before accepting the upload.
- The service stores raw artifacts in Supabase Storage before returning success. In production, Supabase Storage must use an S3-compatible backend so raw coverage files live in remote object storage rather than service-local disk.
- The service creates an internal `upload.received` event and an `analysis_job` in the same transactional boundary as the upload record.
- The response is `202 Accepted` with a stable `upload_id`.
- The caller polls `GET /api/v1/uploads/{upload_id}` until status reaches a terminal state.
- If the HTTP request fails before a `202 Accepted` response, the client retries the same upload with the same `Idempotency-Key`.
- Idempotent retries return the original `upload_id` when the first request was accepted.
- Large/resumable upload support can be added later, but the public contract should still avoid a separate client-visible `complete` call.

Recommended statuses:

- `accepted`: upload metadata and artifacts are durably stored.
- `queued`: internal processing event/job is queued.
- `processing`: downstream analysis is running.
- `completed`: downstream analysis completed.
- `failed`: upload or analysis failed.

## Internal Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/internal/v1/uploads/{upload_id}/artifacts` | List stored artifacts |
| `POST` | `/internal/v1/uploads/{upload_id}/status` | Update processing status |
| `POST` | `/internal/v1/uploads/{upload_id}/parse-errors` | Attach parser errors |

## Request Models

### CreateUploadRequest

Content type: `application/json`

The v0 endpoint accepts metadata and artifact payloads in one JSON request. Artifact contents are Base64-encoded. This keeps the first CLI/API contract simple and lets the service return a durable `upload_id` immediately after storing artifacts.

Multipart or signed-storage handoff can be added later for large/resumable uploads without reintroducing a client-visible `complete` call.

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "commit_sha": "abc123",
  "branch": "main",
  "pull_request_number": 42,
  "ci_provider": "github_actions",
  "ci_build_id": "987654321",
  "ci_build_url": "https://github.com/acme/payments-api/actions/runs/987654321",
  "flags": ["unit", "linux"],
  "component": "api",
  "package": "services/api",
  "artifacts": [
    {
      "name": "lcov.info",
      "kind": "coverage",
      "format": "lcov",
      "content_type": "text/plain",
      "content_base64": "VE46ClNGOnNyYy9NYWluLmphdmEK"
    },
    {
      "name": "junit.xml",
      "kind": "test_results",
      "format": "junit",
      "content_type": "application/xml",
      "content_base64": "PHRlc3RzdWl0ZS8+"
    }
  ]
}
```

## Response Models

### CreateUploadResponse

HTTP status: `202 Accepted`

```json
{
  "data": {
    "upload_id": "84cb6d3c-312f-43af-a3fc-5964b2d27626",
    "status": "queued",
    "poll_url": "/api/v1/uploads/84cb6d3c-312f-43af-a3fc-5964b2d27626",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "commit_sha": "abc123",
    "analysis_job_id": "fb0e1e5d-55d7-4f74-9303-7a93400d53a1"
  }
}
```

### UploadStatusResponse

```json
{
  "data": {
    "id": "84cb6d3c-312f-43af-a3fc-5964b2d27626",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "commit_sha": "abc123",
    "status": "queued",
    "analysis_job_id": "fb0e1e5d-55d7-4f74-9303-7a93400d53a1",
    "artifacts": [
      {
        "name": "lcov.info",
        "kind": "coverage",
        "format": "lcov",
        "status": "stored",
        "size_bytes": 123456
      }
    ],
    "created_at": "2026-05-22T10:00:00Z"
  }
}
```

## Database Models

### `uploads`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Git commit |
| `branch` | text | Branch name |
| `pull_request_number` | integer | Nullable |
| `ci_provider` | text | Provider name |
| `ci_build_id` | text | Provider build ID |
| `ci_build_url` | text | Build URL |
| `flags` | text[] | Report flags |
| `component` | text | Nullable component |
| `package` | text | Nullable package path/name |
| `status` | text | `accepted`, `queued`, `processing`, `completed`, `failed` |
| `idempotency_key` | text | Safe retry key |
| `api_key_id` | uuid | Nullable FK to repository_api_keys |
| `accepted_at` | timestamptz | Accepted time |
| `analysis_job_id` | uuid | Nullable FK to analysis_jobs |
| `failure_code` | text | Nullable failure code |
| `failure_message` | text | Nullable safe error |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `upload_artifacts`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `upload_id` | uuid | FK to uploads |
| `name` | text | Artifact name |
| `kind` | text | `coverage`, `test_results`, `metadata` |
| `format` | text | `lcov`, `cobertura`, `junit`, etc. |
| `content_type` | text | MIME type |
| `size_bytes` | bigint | Size |
| `sha256` | text | Checksum |
| `storage_bucket` | text | Supabase Storage bucket |
| `storage_path` | text | Supabase Storage object path |
| `status` | text | `stored`, `verified`, `failed` |
| `created_at` | timestamptz | Created time |

### `upload_events`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `upload_id` | uuid | FK to uploads |
| `event_type` | text | `upload.received`, `upload.processing_started`, `upload.completed`, `upload.failed` |
| `payload` | jsonb | Event payload |
| `created_at` | timestamptz | Event time |

### `analysis_jobs`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Git commit |
| `job_type` | text | `coverage_analysis`, `test_results_analysis` |
| `status` | text | `queued`, `leased`, `running`, `completed`, `failed`, `canceled` |
| `priority` | integer | Queue priority |
| `payload` | jsonb | Job descriptor |
| `available_at` | timestamptz | Delay/retry scheduling |
| `attempt_count` | integer | Retry count |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

## Supabase Storage Buckets

| Bucket | Purpose |
| --- | --- |
| `coverage-raw` | Raw uploaded coverage reports |
| `test-results-raw` | Raw JUnit/test result files |
| `coverage-normalized` | Normalized compressed coverage maps |
| `agent-artifacts` | Agent dry-run and execution artifacts |

Storage implementation:

- Upload service writes raw artifacts through the `ArtifactStorage` port.
- `VERICOV_ARTIFACT_STORAGE_BACKEND=supabase` enables the Supabase Storage adapter.
- Supabase Storage should be configured with `STORAGE_BACKEND=s3` for production or enterprise self-hosted deployments.
- Object paths are scoped as `{tenant_id}/{upload_id}/{artifact_kind}/{artifact_name}`.

## Events Published

| Event | Trigger |
| --- | --- |
| `upload.received` | Direct upload accepted and artifacts stored |
| `upload.artifact.stored` | Artifact stored in Supabase Storage |
| `analysis_job.created` | Processing job queued |
| `upload.completed` | Downstream analysis completed |
| `upload.failed` | Upload or downstream processing failed |

## Open Questions

- When should direct JSON uploads graduate to multipart or a one-call signed Supabase Storage handoff behind the service?
- What is the maximum raw artifact size for v1?
- Should tokenless CI uploads be supported in the first implementation milestone?
