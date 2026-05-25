# Vericov Coverage Upload CLI

This folder contains the independently buildable Python CLI for uploading coverage
and test-result artifacts to Vericov.

It installs the `vericov` console script and currently owns these commands:

```bash
vericov --version
vericov config validate
vericov upload
```

## Why This Folder Exists

The root Python package reserves the `vericov` distribution name for future
shared Python APIs. This CLI is intentionally separate because coverage upload is
a CI-facing tool with its own release cadence, dependencies, tests, and failure
modes.

Keeping it under `clis/coverage-upload/` gives us room to add future independent
CLIs as sibling packages without turning one Python project into a junk drawer:

```text
clis/
  coverage-upload/
  repository-admin/
  agent-runner/
  report-export/
```

## How Customers Use It

The normal CI path is:

```bash
export VERICOV_API_KEY=vc_live_...
vericov upload --coverage coverage/lcov.info --wait
```

When a repo-scoped API key is used, `repository_id` can be omitted. The upload
service resolves repository identity from the key. Customers can still pass an
explicit repository id when they need to debug or use broader credentials:

```bash
vericov upload \
  --repository-id 4d607f16-1af7-4d3b-ac38-06454cba463c \
  --coverage coverage/lcov.info \
  --test-results junit.xml
```

For local validation without sending data:

```bash
vericov upload --coverage coverage/lcov.info --commit-sha abc123 --branch main --dry-run
```

For machine-readable output:

```bash
vericov upload --coverage coverage/lcov.info --json
```

## Configuration

The canonical config file is `vericov.yml`. `.vericov.yml` is also accepted, but
having both files in the same project is an error.

```yaml
version: 1

api:
  url: https://api.vericov.dev

upload:
  flags:
    - unit
    - linux
  component: api
  package: services/api
  coverage:
    - coverage/lcov.info
  test_results:
    - junit.xml
```

Do not put API keys or tokens in this file. Use `VERICOV_API_KEY` or your CI
secret store.

Validate config with:

```bash
vericov config validate
```

## Development

This package builds and tests from its own folder:

```bash
cd clis/coverage-upload
python3 -m pip install -e '.[dev]'
python3 -m pytest
python3 -m build
```

The package name is `vericov-coverage-upload`. The import package is
`vericov_coverage_upload`. Do not import from the root `src/vericov` package.

## Package Layout

```text
src/vericov_coverage_upload/
  cli/              Typer app, command registration, options, exit handling
  application/      Upload/config/wait use cases
  domain/           Immutable values, validation errors, request models
  infrastructure/   YAML, filesystem, CI/git metadata, HTTP URL gateway
  presentation/     Human and JSON output renderers
```

The CLI is structured as if many more commands may be added later:

- Put new command modules in `cli/commands/`.
- Put reusable options in `cli/options/`.
- Keep Typer callbacks thin.
- Put business logic in `application/`.
- Put side effects in `infrastructure/`.
- Keep output formatting in `presentation/`.

## API Client Scope

V1 calls upload service URLs directly through
`infrastructure/http/direct_url_upload_gateway.py`.

That direct URL adapter is deliberately narrow. A future shared or generated
Vericov API client can replace it by implementing the same gateway shape without
changing Typer commands, artifact discovery, idempotency generation, or output
rendering.

## Improvement Scope

Near-term improvements that fit this package:

- More CI metadata providers.
- Richer coverage/test-result format detection.
- `--wait gates` once gate status is exposed.
- A GitHub Action wrapper that invokes this CLI.
- Multipart or signed-storage upload once the backend supports it.
- A shared Vericov API client dependency.

Work that should not be added here:

- Backend coverage parsing.
- Repository authorization policy.
- API key creation and rotation workflows.
- Long-running agent orchestration.

Those belong to backend services or separate CLI packages.

## What Can Break If You Are Careless

- Logging `VERICOV_API_KEY` leaks customer credentials into CI logs.
- Sending path-like artifact names can make the backend reject uploads.
- Changing idempotency material can duplicate uploads during retries.
- Broad file discovery can upload stale or unrelated reports.
- Following symlinks can leak files outside the repository.
- Retrying `400`, `401`, or `403` hides real user/configuration problems.
- Moving business logic into Typer callbacks makes future command growth painful.
- Importing the root `vericov` package couples this independent CLI to a package
  that intentionally has a different release path.

When in doubt, preserve the boring CI contract: deterministic inputs, clear
errors, no secret leakage, and safe retries.
