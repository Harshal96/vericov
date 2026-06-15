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

The backend is distributed as containers, while coverage upload is a CI-facing
Python tool with its own release cadence, dependencies, tests, and failure
modes. This folder is the only Python distribution published by this repo.

Keeping it under `clis/coverage-upload/` gives us room to add future independent
CLIs as sibling packages without turning one Python project into a junk drawer:

```text
clis/
  coverage-upload/
  report-export/
```

## Typical Use

The normal CI path is:

```bash
export VERICOV_API_URL=https://vericov.example.internal
export VERICOV_API_KEY=vc_repo_...
uvx --from vericov-coverage-upload vericov upload --coverage coverage/lcov.info --wait
```

When a repo-scoped API key is used, `repository_id` can be omitted. The upload
service resolves repository identity from the key. Customers can still pass an
explicit repository id when they need to debug or use broader credentials:

```bash
uvx --from vericov-coverage-upload vericov upload \
  --repository-id 00000000-0000-0000-0000-000000000003 \
  --coverage coverage/lcov.info \
  --test-results junit.xml
```

For local validation without sending data:

```bash
VERICOV_API_KEY=vc_repo_... uv run vericov upload --coverage coverage/lcov.info --commit-sha abc123 --branch main --dry-run
```

For machine-readable output:

```bash
uvx --from vericov-coverage-upload vericov upload --coverage coverage/lcov.info --json
```

## Configuration

The only supported configuration filename is `.vericov.yml`. The CLI discovers
it automatically from the current project. A legacy `vericov.yml`, or an
explicit `--config` path with another filename, fails with an actionable rename
error.

```yaml
version: 1

ignore:
  - generated/**
  - vendor/**
  - "!vendor/maintained/**"

components:
  - key: commerce
    name: Commerce
    owners:
      - team-commerce
    gates:
      line: 80
      branch: 70
    components:
      - key: payments
        name: Payments
        components:
          - key: payments-api
            name: Payments API
            owners:
              - team-payments
            gates:
              line: 90
            paths:
              - services/payments/api/**
          - key: payments-web
            name: Payments Web
            paths:
              - services/payments/web/**

api:
  url: https://vericov.example.internal

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

The top-level `ignore` list filters source paths after Vericov parses coverage
artifacts and before it merges reports. Paths are normalized to
repository-relative `/` separators and matching is case-sensitive. Rules run in order, so
a later rule overrides an earlier one; a leading `!` re-includes a path. A
leading `/` anchors a rule at the repository root, a trailing `/` matches a
directory and its descendants, and patterns without `/` match at any depth.
The `*`, `?`, character-range, and `**` wildcards use gitignore-style behavior.

The optional top-level `components` list defines a hierarchy for monorepos.
Parent components contain only nested `components`; leaf components contain
only `paths`. Keys must be stable, globally unique lowercase identifiers.
Owners inherit from the nearest ancestor unless replaced, and `line`, `branch`,
`function`, and `statement` gates inherit per metric.

Vericov applies `ignore` rules before component matching. Every remaining file
is assigned to the most-specific matching leaf. Equal-specificity matches fail
the upload analysis as ambiguous; unmatched files are reported in a synthetic
root component named `unassigned`. Parent coverage and gates include all
descendant files, while leaf coverage includes directly assigned files.

The upload stores a canonical snapshot and `config_sha256`, so reports remain
reproducible even after `.vericov.yml` changes. Report gate failures do not make
analysis fail: the upload completes and the report returns
`gate_status: failed`, component evaluations, and any `unassigned_files`
warning.

This is separate from `upload.discover.exclude`: `ignore` removes source files
from coverage analysis, while `upload.discover.exclude` prevents artifact files
from being discovered and uploaded.

Configuration changes affect future uploads only; existing reports are not
recomputed.

If every coverage source file is excluded, analysis still succeeds with an
empty `0/0` coverage report. Test-result artifacts in the same upload continue
to be processed.

Do not put API keys or tokens in this file. Use `VERICOV_API_KEY` or your CI
secret store.

Validate config with:

```bash
uv run vericov config validate
```

## Development

This package builds and tests from its own folder:

```bash
cd clis/coverage-upload
uv sync --dev
uv run pytest
uv build
```

The package name is `vericov-coverage-upload`. The import package is
`vericov_coverage_upload`. Do not import from the root `src/vericov` package.

Use `uv` for all local package operations. Do not add pip, virtualenv, poetry,
or ad hoc Python command instructions to this folder. If you add or change
dependencies, update `pyproject.toml` and run `uv lock`.

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
- A shared Vericov API client dependency managed through `uv`.

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
- Adding backend-only concerns couples a small CI tool to the service release
  lifecycle.

When in doubt, preserve the boring CI contract: deterministic inputs, clear
errors, no secret leakage, and safe retries.
