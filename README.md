# Vericov

Vericov is an agentic coverage backend for coverage reporting, merge
confidence, policy gates, and autonomous test-remediation workflows. It ships as
a self-hostable backend stack and as a managed module that can sit behind
veriapi alongside other services.

## Quick Start

```bash
cp .env.example .env
./vericov doctor
./vericov up
```

Self-hosting has no required auth-provider setup. The default `.env.example`
uses `VERICOV_DEV_AUTH_BYPASS=true` for trusted private deployments. Disable the
bypass and configure a service-JWT key when a gateway such as veriapi is minting
delegated identities.

## Guides

- [Self-hosting](docs/SELF_HOSTING.md)
- [Managed integration](docs/MANAGED_INTEGRATION.md)
- [Coverage upload CLI](clis/coverage-upload/README.md)

## Services

| Service | Purpose | Port |
| --- | --- | --- |
| upload | CI coverage artifact ingestion | 8080 |
| coverage-analysis | Coverage parsing, normalization, reports, gates | 8081 |
| control-plane | Repositories, config, policies, badges, debt, dashboards | 8082 |
| git-integration | Provider webhooks and git actions | 8083 |
| integrations | Provider connections, credentials, bindings | 8084 |
| agent-runner | Agent task control plane and runner protocol | 8085 |

The repo no longer bundles a product Kong gateway. Put your own gateway or
private network boundary in front of the direct service ports.

## Coverage Upload CLI

The coverage upload CLI remains an independent Python package under
`clis/coverage-upload`:

```bash
cd clis/coverage-upload
VERICOV_API_KEY=vc_live_... uv run vericov upload --coverage coverage/lcov.info --dry-run
```
