# Self-Hosting Vericov

Vericov self-hosts as a backend-only stack: five Helidon services plus optional
bundled Postgres and optional bundled Supabase storage. There is no required
auth-provider setup for self-hosting.

## Quick Start

```bash
git clone <your-vericov-repo>
cd vericov
cp .env.example .env
./vericov doctor
./vericov up
```

The default `.env.example` uses `VERICOV_DEV_AUTH_BYPASS=true`. Put the services
behind your own private gateway, VPN, or internal network boundary. When you are
ready to delegate identity from a gateway, set `VERICOV_DEV_AUTH_BYPASS=false`
and configure the service-JWT key described in `docs/MANAGED_INTEGRATION.md`.

## What Starts

`./vericov up` starts the Vericov services on direct localhost ports:

| Service | Port |
| --- | --- |
| upload | 8080 |
| coverage-analysis | 8081 |
| control-plane | 8082 |
| git-integration | 8083 |
| integrations | 8084 |
| agent-runner | 8085 |

If `BYO_POSTGRES=0`, the command also starts a bundled Postgres container. If
`BYO_SUPABASE=0`, it starts the existing `infra/supabase` stack for local
Supabase storage. `BYO_SUPABASE=skip` is the no-auth/no-Supabase default.

## Bring Your Own Postgres

Set:

```env
BYO_POSTGRES=1
VERICOV_DB_URL=jdbc:postgresql://postgres.example.internal:5432/vericov
VERICOV_DB_USER=vericov
VERICOV_DB_PASSWORD=...
```

Then run:

```bash
./vericov doctor
./vericov up
```

## Storage

For first boot, `VERICOV_ARTIFACT_STORAGE_BACKEND=memory` avoids external
storage setup. For durable uploads, configure Supabase storage:

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
service-JWT minting. See `docs/MANAGED_INTEGRATION.md` for that token format.

## Operations

```bash
./vericov status
./vericov logs control-plane
./vericov migrate
./vericov down
```

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
