# Operations

This runbook covers the default self-hosted stack. Keep `.env`, database
backups, and artifact backups private.

## Daily Commands

```bash
./vericov status
./vericov logs upload
./vericov logs coverage-analysis
./scripts/smoke-test.sh
```

The smoke test submits a small LCOV report and waits for analysis to complete.
It is a functional check, not just a health-endpoint check.

## Backups

The bundled database is published on `127.0.0.1:54329` by default:

```bash
set -a
. ./.env
set +a

PGPASSWORD="$VERICOV_DB_PASSWORD" pg_dump \
  --host localhost \
  --port "${VERICOV_POSTGRES_PORT:-54329}" \
  --username postgres \
  --format custom \
  --file vericov.dump \
  postgres
```

Back up the `vericov-artifacts` Docker volume separately. For bring-your-own
PostgreSQL or Supabase Storage, use the backup facilities supplied by that
operator.

## Restore

Stop the application services before restoring:

```bash
./vericov down
```

Restore the database into an empty compatible PostgreSQL instance with
`pg_restore`, restore the artifact volume to the same relative paths, then run:

```bash
./vericov migrate
./vericov up
./scripts/smoke-test.sh
```

Database rows and artifact files must come from the same backup window.

## Upgrades

For this pre-1.0 release, take a database and artifact backup before every
upgrade:

```bash
./vericov down
git pull --ff-only
./vericov migrate
./vericov up
./scripts/smoke-test.sh
```

Read release notes for schema or environment changes before starting the new
containers.

## Security

- Direct service ports bind to localhost by default.
- Do not expose them publicly without TLS, authentication, request-size
  limits, and rate limiting at a gateway.
- Replace `VERICOV_DEV_AUTH_BYPASS=true` before serving untrusted clients.
- Store `.env` with mode `0600`; `./vericov init` does this automatically.
- Rotate API keys and JWT secrets if `.env` or CI logs are exposed.

## Failure Recovery

If an upload remains queued, inspect `coverage-analysis` logs and confirm the
database has the `pgmq` extension and both coverage queues. If analysis fails,
the upload status and job error are persisted in PostgreSQL. Correct the
configuration or artifact issue, then submit a new upload with a new
idempotency key.
