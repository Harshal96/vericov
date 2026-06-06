# Vericov Self-Hosted Supabase

This folder contains Vericov's local self-hosted Supabase stack for backend development. It uses Docker Compose and keeps the stack narrow: Postgres, Auth, REST, Storage, Studio, Meta, imgproxy, and Kong.

Analytics, Vector, Realtime, Edge Functions, and TLS proxy overlays are intentionally not part of this first stack. Raw artifact storage is routed through Supabase Storage and can be backed by AWS S3, Cloudflare R2, MinIO, RustFS, or another S3-compatible object store.

## First Boot

```bash
cd infra/supabase
node scripts/generate-env.mjs
docker compose pull
docker compose up -d
docker compose ps
```

To bring up the full Vericov local backend stack, including this Supabase stack, all Helidon services, and the product Kong gateway, run from the repository root:

```bash
./scripts/dev-up.sh
```

Open Supabase Studio at `http://localhost:8000`. The username is `DASHBOARD_USERNAME` from `.env`, and the password is generated into `DASHBOARD_PASSWORD`.

## Local Endpoints

| Endpoint | URL |
| --- | --- |
| Supabase API gateway / Studio | `http://localhost:8000` |
| Auth | `http://localhost:8000/auth/v1` |
| REST | `http://localhost:8000/rest/v1` |
| Storage | `http://localhost:8000/storage/v1` |
| Postgres direct host port | `localhost:54322` |

## Vericov Bootstrap

On first database initialization, `volumes/db/vericov.sql` creates:

- Private `vericov` schema
- Organization and upload service tables: tenants, organizations, memberships, organization invitations, repositories, API keys, uploads, upload artifacts, analysis jobs, and upload events
- PGMQ queues: `coverage_analysis_jobs` and `coverage_analysis_dead_letters`
- `vericov.enqueue_coverage_analysis_job(uuid)`, the trusted database function that emits an `upload.received` queue message for an analysis job
- Private Supabase Storage buckets: `coverage-raw`, `coverage-normalized`, `test-results-raw`, and `metadata-raw`

The `vericov` schema is not exposed through PostgREST. It is intended for trusted backend services over JDBC or a future server-side adapter using the service key.

## Remote Raw Artifact Storage

The upload service writes raw coverage files, test result files, and metadata files to Supabase Storage. For production and enterprise self-hosting, configure Supabase Storage with an S3 backend so raw artifacts live outside the service container and Postgres volume.

For AWS S3:

```bash
STORAGE_BACKEND=s3
GLOBAL_S3_BUCKET=<aws-s3-bucket>
REGION=<aws-region>
AWS_ACCESS_KEY_ID=<storage-writer-access-key>
AWS_SECRET_ACCESS_KEY=<storage-writer-secret-key>
```

For S3-compatible providers such as R2, MinIO, or RustFS, also set:

```bash
GLOBAL_S3_ENDPOINT=<provider-endpoint>
GLOBAL_S3_PROTOCOL=https
GLOBAL_S3_FORCE_PATH_STYLE=true
```

Local development may keep `STORAGE_BACKEND=file`, but production should use `s3`.

## Upload Service Env Shape

The Java upload service uses these values to store raw artifacts in Supabase Storage. If Supabase Storage itself is configured with `STORAGE_BACKEND=s3`, those raw files land in the remote S3-compatible backend.

```bash
SUPABASE_URL=http://localhost:8000
SUPABASE_SERVICE_ROLE_KEY=<from infra/supabase/.env>
VERICOV_ARTIFACT_STORAGE_BACKEND=supabase
VERICOV_DB_URL=jdbc:postgresql://localhost:54322/postgres
VERICOV_DB_USER=postgres
VERICOV_DB_PASSWORD=<POSTGRES_PASSWORD from infra/supabase/.env>
VERICOV_UPLOAD_DB_URL=jdbc:postgresql://localhost:54322/postgres
VERICOV_UPLOAD_DB_USER=postgres
VERICOV_UPLOAD_DB_PASSWORD=<POSTGRES_PASSWORD from infra/supabase/.env>
VERICOV_REPO_API_KEY_PEPPER=<long random secret distinct from JWT_SECRET>
VERICOV_RUNNER_JWT_SECRET=<long random secret for short-lived upload tokens>
VERICOV_RUNNER_JWT_ISSUER=vericov-upload
VERICOV_RUNNER_JWT_AUDIENCE=vericov-runner-upload
VERICOV_GITHUB_ACTIONS_OIDC_JWKS_URL=https://token.actions.githubusercontent.com/.well-known/jwks
VERICOV_COVERAGE_BUCKET=coverage-raw
VERICOV_TEST_RESULTS_BUCKET=test-results-raw
VERICOV_METADATA_BUCKET=metadata-raw
```

When `VERICOV_UPLOAD_DB_URL` is present, upload authentication uses Supabase Postgres-backed repository API keys and repository trust records. `VERICOV_DEV_API_KEY` is only a local bypass when `VERICOV_DEV_AUTH_BYPASS=true`.

## Control Plane Service Env Shape

The control-plane service uses JDBC for persistent repository/config/policy data.
Without these values it runs with in-memory storage for local smoke tests.

```bash
VERICOV_CONTROL_PLANE_DB_URL=jdbc:postgresql://localhost:54322/postgres
VERICOV_CONTROL_PLANE_DB_USER=postgres
VERICOV_CONTROL_PLANE_DB_PASSWORD=<POSTGRES_PASSWORD from infra/supabase/.env>
VERICOV_DEV_AUTH_BYPASS=true
VERICOV_DEV_USER_ID=<local user uuid for bypassed requests>
```

Self-hosting does not require Supabase Auth. Managed deployments should disable
the bypass and configure `VERICOV_SERVICE_JWT_PUBLIC_KEY`.

## Coverage Analysis Worker Env Shape

The coverage analysis service consumes `upload.received` messages from PGMQ. It is safe to start with only the HTTP shell, but the queue worker turns on automatically when `VERICOV_ANALYSIS_DB_URL` is present. The current processor supports LCOV, Cobertura, JaCoCo, Clover, Go cover profile, gcov, and llvm-cov gcov coverage artifacts, plus JUnit XML test-result artifacts.

```bash
VERICOV_ANALYSIS_DB_URL=jdbc:postgresql://localhost:54322/postgres
VERICOV_ANALYSIS_DB_USER=postgres
VERICOV_ANALYSIS_DB_PASSWORD=<POSTGRES_PASSWORD from infra/supabase/.env>
SUPABASE_URL=http://localhost:8000
SUPABASE_SERVICE_ROLE_KEY=<from infra/supabase/.env>
VERICOV_ANALYSIS_QUEUE_NAME=coverage_analysis_jobs
VERICOV_ANALYSIS_DEAD_LETTER_QUEUE_NAME=coverage_analysis_dead_letters
VERICOV_ANALYSIS_WORKER_ID=coverage-analysis-local
VERICOV_ANALYSIS_VISIBILITY_TIMEOUT_SECONDS=300
VERICOV_ANALYSIS_BATCH_SIZE=10
VERICOV_ANALYSIS_WORKER_IDLE_DELAY_MS=1000
VERICOV_NORMALIZED_COVERAGE_BUCKET=coverage-normalized
```

The worker downloads raw coverage and test-result files from private Supabase Storage buckets using the service-role key, merges shards by repository file path, writes a gzip-compressed normalized coverage map to `coverage-normalized`, evaluates active project coverage gates, writes `coverage_reports`, `coverage_file_summaries`, `coverage_line_hits`, `test_runs`, and `gate_evaluations`, marks the upload processed, and emits `coverage.report.completed`, `test.runs.completed`, plus `coverage.gates.evaluated` upload events when applicable.

## Reset

This destroys local data:

```bash
cd infra/supabase
docker compose down -v
rm -rf volumes/db/data volumes/storage
```

Run `node scripts/generate-env.mjs --force` if you also want new local secrets.

## Security Notes

- Do not commit `.env`; it contains database passwords and service-role credentials.
- Never expose `SERVICE_ROLE_KEY` or `SUPABASE_SECRET_KEY` to clients.
- The compose file binds Kong and Postgres to `127.0.0.1` for local development.
- `vericov` tables have RLS enabled and no `anon` or `authenticated` grants. Backend services should use trusted server-side credentials.
- This stack pins `supabase/postgres:15.8.1.085`. Supabase has announced a self-hosted default move to Postgres 17 in June 2026; use a fresh volume or an upgrade path before changing that image tag.
