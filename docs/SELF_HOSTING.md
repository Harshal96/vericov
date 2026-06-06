# Self-Hosting Vericov

Vericov self-hosts as a backend-only stack: three core Helidon services plus optional
bundled Postgres and optional bundled Supabase storage. There is no required
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
behind your own private gateway, VPN, or internal network boundary. When you are
ready to delegate identity from a gateway, set `VERICOV_DEV_AUTH_BYPASS=false`
and configure the service-JWT key described in
[Gateway authentication](GATEWAY_AUTH.md).

## What Starts

`./vericov up` starts the Vericov services on direct localhost ports:

| Service | Port |
| --- | --- |
| upload | 8080 |
| coverage-analysis | 8081 |
| control-plane | 8082 |

The `integrations` and `agents` Compose profiles are excluded from the public
quick start while those optional surfaces are simplified.

If `BYO_POSTGRES=0`, the command also starts a pinned Supabase Postgres
container with the Vericov schema and PGMQ queues initialized. If
`BYO_SUPABASE=0`, it starts the full `infra/supabase` stack for local Supabase
storage. `BYO_SUPABASE=skip` is the no-auth/no-Supabase default.

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

## Gateway

Vericov no longer bundles a product Kong gateway. Put your own gateway or
reverse proxy in front if you expose services outside a private network. The
gateway should handle TLS, rate limits, request size limits, and optional
service-JWT minting. See [Gateway authentication](GATEWAY_AUTH.md) for that
token format.

## Operations

```bash
./vericov status
./vericov logs control-plane
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
settings, storage/auth mismatches, and missing service-JWT keys when auth bypass
is disabled.
