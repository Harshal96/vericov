# Self-Hosting Vericov

Vericov self-hosts as a backend-only stack: two Helidon services plus bundled
or bring-your-own Postgres. Filesystem artifact storage is included, and an
existing Supabase project can be used instead. There is no required
auth-provider setup for self-hosting.

## Prerequisites

- Docker with Docker Compose
- Git
- `psql` only when migrating a bring-your-own database

## Quick Start

```bash
git clone <your-vericov-repo>
cd vericov
./vericov init
./vericov doctor
./vericov up
```

The generated `.env` uses `VERICOV_DEV_AUTH_BYPASS=true`. Put the services
behind your own private gateway, VPN, or internal network boundary. To use
database-backed repository API keys or GitHub Actions OIDC trust records, set
`VERICOV_DEV_AUTH_BYPASS=false` and configure the corresponding rows in the
tracked schema.

## What Starts

`./vericov up` starts the Vericov services on direct localhost ports:

| Service | Port |
| --- | --- |
| upload | 8080 |
| coverage-analysis | 8081 |

If `BYO_POSTGRES=0`, the command also starts a pinned Supabase Postgres
container with the Vericov schema and PGMQ queues initialized.
`BYO_SUPABASE=skip` is the no-auth/no-Supabase default.

## Bring Your Own Postgres

Set:

```env
BYO_POSTGRES=1
VERICOV_DB_URL=jdbc:postgresql://postgres.example.internal:5432/vericov
VERICOV_DB_USER=your_database_user
VERICOV_DB_PASSWORD=...
```

The database must provide the `pgcrypto` and `pgmq` extensions. Apply the
tracked schema before starting the services:

```bash
./vericov doctor
./vericov migrate
./vericov up
```

Vericov `0.1` expects a fresh database. Earlier development snapshots used a
different schema and are not supported as an in-place upgrade source.

## Storage

The default `filesystem` backend stores raw and normalized artifacts in a
Docker volume shared by upload and analysis. To use Supabase Storage instead:

```env
BYO_SUPABASE=1
VERICOV_ARTIFACT_STORAGE_BACKEND=supabase
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_STORAGE_URL=https://your-project.supabase.co/storage/v1
SUPABASE_SERVICE_ROLE_KEY=...
```

Supabase Auth is not required by Vericov services in the self-host flow.

## Monorepo Components

Repositories can define hierarchical components, owners, path patterns, and
coverage gates in `.vericov.yml`. The upload CLI sends a canonical immutable
snapshot, and both services persist its `config_sha256` with the resulting
report. Apply `./vericov migrate` before using component reports so the
string-key rollups and gate scope columns are available.

Top-level coverage exclusions are evaluated before component assignment.
Component configuration changes affect future uploads only; existing reports
are not recomputed. A failed component gate leaves the upload processed and is
reported through `gate_status`.

## Gateway

Vericov no longer bundles a product Kong gateway. Put your own gateway or
reverse proxy in front if you expose services outside a private network. The
gateway should handle TLS, rate limits, and request size limits.

## Operations

```bash
./vericov status
./vericov logs coverage-analysis
./vericov migrate
./vericov down
```

See the [operations runbook](OPERATIONS.md) for functional smoke checks,
backups, restore, upgrades, and failure recovery.

For upgrades:

```bash
./vericov down
git pull
./vericov migrate
./vericov up
```

## Troubleshooting

Run `./vericov doctor` first. It catches missing `.env`, inconsistent BYO
settings, storage/auth mismatches, and missing repository-key configuration
when auth bypass is disabled.
