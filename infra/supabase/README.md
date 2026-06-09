# Optional Local Supabase Storage

This directory provides an optional local Supabase stack for evaluating
Supabase Storage with Vericov. The default self-hosted path uses the bundled
Postgres container and a shared filesystem volume, so most operators do not
need this stack.

## Start the Evaluation Stack

The root launcher deliberately does not manage this optional stack. Start it
directly when evaluating Supabase Storage:

```bash
cd infra/supabase
node scripts/generate-env.mjs
docker compose up -d
docker compose ps
```

The local API and Studio gateway binds to `127.0.0.1:8000`; Postgres binds to
`127.0.0.1:54322`. Credentials are generated into the ignored
`infra/supabase/.env` file.

To connect Vericov to it, treat the stack as a bring-your-own Supabase
endpoint: set `BYO_SUPABASE=1`, use a URL reachable from the Vericov
containers, and copy `SERVICE_ROLE_KEY` from the generated local environment
to `SUPABASE_SERVICE_ROLE_KEY` in the root `.env`.

## Vericov Bootstrap

On first database initialization, `volumes/db/vericov.sql` creates:

- The private `vericov` schema and single-workspace repository seed.
- Repository credentials and GitHub Actions OIDC trust records.
- Uploads, artifacts, analysis jobs, reports, line hits, test runs, gaps, and
  gate evaluations.
- PGMQ queues for coverage analysis and dead letters.
- Private Storage buckets when the Supabase `storage` schema is available.

The Vericov schema is accessed by the backend services over JDBC. Supabase
Auth is not required.

## Remote Supabase Storage

To use an existing Supabase project instead of this local stack, set:

```env
BYO_SUPABASE=1
VERICOV_ARTIFACT_STORAGE_BACKEND=supabase
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_STORAGE_URL=https://your-project.supabase.co/storage/v1
SUPABASE_SERVICE_ROLE_KEY=...
```

The upload and analysis services use the service-role key only on the server
side. Never expose it to clients.

## Reset

To destroy the optional local Supabase data:

```bash
cd infra/supabase
docker compose down -v
```

Remove the ignored `infra/supabase/.env` only when you intentionally want new
local credentials on the next start.

## Security

- Keep `infra/supabase/.env` private.
- Never expose the service-role key to browsers or CI logs.
- The compose ports bind to localhost by default.
- Review Supabase's production hardening guidance before exposing this stack
  outside a private development machine.
