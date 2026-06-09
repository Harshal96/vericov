# Contributing to Vericov

Thanks for helping improve Vericov. This repository is an open-source,
self-hosted coverage backend. Keep changes useful to public operators and avoid
adding hosted-only, billing, organization-management, or enterprise fleet
features.

## Prerequisites

- Git
- Docker with Docker Compose
- Java 25 and Maven 3.9+ for service development
- Python 3.9+ for the coverage upload CLI
- `psql` when testing bring-your-own Postgres migrations

The default self-hosted path only requires Git and Docker:

```bash
./vericov init
./vericov doctor
./vericov up
```

## Development Checks

Run the same main checks as CI:

```bash
mvn verify
python -m pytest -q
python -m pip install -e clis/coverage-upload pytest pytest-cov
(cd clis/coverage-upload && python -m pytest -q --cov=vericov_coverage_upload --cov-report=term-missing --cov-fail-under=80)
sh -n vericov scripts/*.sh
VERICOV_COMPOSE_ENV_FILE=../../.env.example \
  docker compose --env-file .env.example \
  -f infra/local/docker-compose.yml \
  --profile bundled-db config --quiet
```

For a running stack:

```bash
./scripts/smoke-test.sh --start-stack
```

## Change Guidelines

- Write or update tests before implementation.
- Validate input at HTTP, CLI, webhook, and configuration boundaries.
- Never commit credentials or generated `.env` files.
- Keep service and domain boundaries intact; avoid unrelated refactors.
- Update user or operator documentation when behavior changes.
- Add dependencies only when the standard library or existing dependencies are
  insufficient.

## Pull Requests

Use a conventional commit-style title such as `fix: initialize bundled
database`. Describe the user impact, implementation, and verification. Keep
pull requests focused enough to review and roll back independently.

By contributing, you agree that your contribution is licensed under the MIT
License in this repository.
