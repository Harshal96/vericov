# Upload Service

The upload service is Vericov's public ingestion and report-read boundary. It
authenticates callers, validates upload metadata, stores raw artifacts,
transactionally creates an analysis job, and exposes status, artifact, and
report reads.

## HTTP API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/uploads` | Accept metadata and Base64 artifact contents |
| `GET` | `/api/v1/uploads/{upload_id}` | Poll upload and analysis status |
| `GET` | `/api/v1/uploads/{upload_id}/artifacts` | List stored artifacts |
| `GET` | `/api/v1/uploads/{upload_id}/report` | Read the completed coverage report |
| `POST` | `/api/v1/uploads/auth/runner-token` | Mint a short-lived upload token from an authorized credential |

The default local configuration uses `VERICOV_DEV_API_KEY` on a trusted
private network. With `VERICOV_DEV_AUTH_BYPASS=false`, the service accepts
Postgres-backed repository API keys, short-lived runner upload JWTs, and
configured GitHub Actions OIDC identities.

## Persistence

`JdbcUploadRepository` stores uploads, artifact metadata, analysis jobs, queue
messages, and upload events in one transaction. Artifact bytes are stored in a
shared filesystem volume by default or in Supabase Storage when configured.

The service reads completed report summaries and metrics from PostgreSQL for
the `/report` response. Detailed normalized maps remain in artifact storage.

## Source Map

```text
api/             JAX-RS resources and HTTP records
application/     Upload workflow and authorization
application/port Persistence and authentication contracts
domain/          Upload commands, artifacts, principals, and status
adapter/auth/    API key, JWT, and GitHub Actions OIDC verification
adapter/jdbc/    Transactional upload and report persistence
adapter/storage/ Filesystem and Supabase artifact storage
config/          CDI wiring for database-backed and local modes
```

## Tests

Run the module, including its 80% line-coverage gate:

```bash
mvn -pl services/upload verify
```
