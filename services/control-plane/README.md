# Control Plane Service

The control-plane service is the Vericov backend module that owns repository
registration, repository configuration, policy and gate configuration, API keys,
badge settings, coverage-debt workflow, and dashboard-shaped read models.

It replaces the former `services/organization` module. Self-hosted deployments
do not require an auth provider; `VERICOV_DEV_AUTH_BYPASS=true` lets a trusted
private gateway or operator network inject the local development user context.
Managed deployments should disable the bypass and provide the service-JWT
public key minted by veriapi.

## Split Decision Matrix

| Surface | Owner | Notes |
| --- | --- | --- |
| repositories | control-plane | Repository identity and lifecycle |
| repository_configs | control-plane | UI/repo-file config records |
| repository_policies | control-plane | Policy definitions and status |
| repository_gate_configurations | control-plane | Gate thresholds and blocking behavior |
| repository_api_keys | control-plane + upload | Control-plane creates; upload authenticates |
| repository_ci_trusts | control-plane + upload | Trust metadata for CI OIDC uploads |
| components / owner rules / package nodes | control-plane | Repository context used by analysis and reports |
| repository_badge_settings / badge_cache | control-plane | Badge settings and cached badge responses |
| coverage_debt_items / events | control-plane | Debt lifecycle and audit trail |
| coverage_reports / file summaries / line hits | coverage-analysis | Produced and persisted by coverage-analysis |
| pull_request_coverage_* | coverage-analysis | PR diff coverage data plane |
| gate_evaluations | coverage-analysis | Produced by analysis; surfaced by dashboards |
| organizations / memberships / invitations | deleted | Not part of self-hosted Vericov auth model |
| organization_policy_defaults | deleted | Effective config collapses to repository config and gates |
| gateway_route_registry / gateway_audit_events | deleted | Product gateway is external to this repo |

## Current Implementation Note

The module has been mechanically relocated from the former organization service
so existing repository/config/policy behavior remains buildable during the
packaging migration. The public contract is now control-plane and service-JWT
based; deeper route/schema detenancy should continue behind this module boundary
without reintroducing a separate organization service.
