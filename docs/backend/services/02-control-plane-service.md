# Control Plane Service

Status: active backend module
Runtime: Helidon 4
Public identity: service JWT from veriapi/customer gateway, or dev bypass for
self-host/private deployments

## Purpose

The control-plane service owns repository registration, repository configuration,
repository policies, gate configuration, repository API keys, badge settings,
coverage debt, and dashboard aggregation.

It replaces the former organization service. Organization membership,
invitation, and Supabase Auth validation are not part of self-hosted Vericov.

## Auth Boundary

- Self-host/no-auth setup: `VERICOV_DEV_AUTH_BYPASS=true`.
- Managed setup: `VERICOV_DEV_AUTH_BYPASS=false` and
  `VERICOV_SERVICE_JWT_PUBLIC_KEY` configured.
- Symmetric fallback: `VERICOV_SERVICE_JWT_SECRET` for single-tenant private
  deployments.

Services do not validate end-user Supabase JWTs.

## Runtime Ports

| Protocol | Port |
| --- | --- |
| REST | 8082 |
| gRPC contract | 50082 |

## Owned Surfaces

| Surface | Notes |
| --- | --- |
| repositories | provider, full name, visibility, default branch, status |
| repository_configs | UI/repo-file/computed configuration |
| repository_policies | repository/component/path policy rules |
| repository_gate_configurations | gate thresholds and blocking flags |
| repository_api_keys | CI upload key lifecycle |
| repository_ci_trusts | trusted CI OIDC grants |
| components / owner rules / package nodes | repository ownership context |
| repository_badge_settings / badge_cache | coverage badge configuration |
| coverage_debt_items / events | coverage-debt lifecycle |

Coverage reports, line hits, PR diff coverage, test runs, and gate evaluations
are produced by coverage-analysis. Control-plane may aggregate those read models
for dashboards.

## Deleted Surfaces

- organizations
- memberships
- invitations
- organization policy defaults
- product gateway route registry/audit records

## Current REST Compatibility

The implementation is currently a moved control-plane module with legacy route
compatibility preserved while the schema/routes are detenanting. New integration
work should target repository-scoped control-plane contracts and should not
reintroduce a standalone organization service.
