# Vericov

Vericov is an open-source, self-hosted coverage backend for coverage uploads,
normalized reports, policy gates, merge confidence, and test-remediation
workflows.

> **Project status:** pre-1.0. The core services and upload CLI are usable, but
> APIs and database schemas may still change between releases.

## Features

- Upload coverage and test-result artifacts from CI.
- Parse LCOV, JaCoCo, Cobertura, Clover, Go cover, gcov, and llvm-cov output.
- Store normalized reports, line hits, test runs, coverage gaps, and gates.
- Run with an integrated PostgreSQL database or bring your own compatible
  PostgreSQL instance.
- Start without an external auth provider on a trusted private network.
- Use shared filesystem storage by default or Supabase Storage when preferred.

## Quick Start

Prerequisites: Git, Docker, and Docker Compose.

```bash
git clone https://github.com/Harshal96/vericov.git
cd vericov
./vericov init
./vericov doctor
./vericov up
```

The bundled database initializes the tracked Vericov schema and PGMQ queues on
first boot. Services bind to `127.0.0.1` by default.

Verify the stack:

```bash
./scripts/smoke-test.sh
```

Submit a report from the checkout:

```bash
python -m pip install -e clis/coverage-upload
set -a
. ./.env
set +a
VERICOV_API_URL=http://localhost:8080 \
VERICOV_API_KEY="$VERICOV_DEV_API_KEY" \
vericov upload --coverage coverage/lcov.info --wait
```

Stop it without deleting data:

```bash
./vericov down
```

See [Self-hosting](docs/SELF_HOSTING.md) for configuration and
[Operations](docs/OPERATIONS.md) for backups, upgrades, recovery, and security.

## Architecture

| Service | Purpose | Port |
| --- | --- | --- |
| upload | CI coverage artifact ingestion | 8080 |
| coverage-analysis | Coverage parsing, normalization, reports, gates | 8081 |
| control-plane | Repositories, config, policies, badges, debt, dashboards | 8082 |

Provider integrations are currently optional and are not part of the default
three-service startup.

Vericov does not expose a bundled public gateway. Keep direct service ports on
a private network or put your own TLS, authentication, and rate-limiting proxy
in front of them.

## Upload CLI

The Python upload CLI is independently packaged as `vericov-coverage-upload`:

```bash
uvx --from vericov-coverage-upload vericov upload \
  --coverage coverage/lcov.info \
  --dry-run
```

See the [CLI guide](clis/coverage-upload/README.md) for configuration and CI
usage.

## Development

```bash
mvn test
python -m pytest -q
python -m pip install -e clis/coverage-upload pytest
(cd clis/coverage-upload && python -m pytest -q)
```

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Report
security issues through the private process in [SECURITY.md](SECURITY.md).

## License

Vericov is available under the [MIT License](LICENSE).
