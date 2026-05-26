# Organization Service

The Organization service is Vericov's API control plane. It owns tenant-facing
organization state, repository registration, memberships, policy defaults,
repository configuration, API keys, badge settings, coverage debt, dashboard
reads, and report read APIs.

The fuller contract lives in
`docs/backend/services/02-api-control-plane-service.md`.

## Why This Service Exists

Most product requests need authorization against an organization and repository.
This service centralizes that ownership model so ingestion, analysis, Git
actions, and agent workflows can reference one canonical control plane instead
of each maintaining their own tenant and repository model.

## Current Architecture

```text
                 user JWT or dev auth headers
                             |
                             v
+----------------+    +------+-------------------+
| Web app / CLI  | -> | Kong /api/v1/orgs,/auth  |
+----------------+    +------+-------------------+
                             |
                             v
                  +----------+-------------+
                  | OrganizationResource   |
                  | RepositoryControlPlane |
                  | Auth/Authz/Internal    |
                  +----------+-------------+
                             |
                             v
                  +----------+-------------+
                  | OrganizationApplication|
                  | Service                |
                  +----------+-------------+
                             |
                 +-----------+------------+
                 |                        |
                 v                        v
       +---------+----------+    +--------+---------+
       | OrganizationRepo   |    | UserPrincipal    |
       | JDBC or in-memory  |    | Resolver         |
       +--------------------+    +------------------+
```

## Where It Is Called From

```text
Human/API client
  -> GET /api/v1/auth/me
  -> create org, invite members, register repositories
  -> configure policies, gates, badge settings, API keys

Upload service
  -> validates repo API keys created by organization service data
  -> associates uploads with canonical repository_id

Coverage analysis
  -> persists reports/gates/test runs in shared storage
  -> organization service serves read APIs and dashboards

Git and Integrations services
  -> use repository/org identity and authorization context
  -> internal authz endpoint answers service authorization checks
```

## Data Model

```text
organizations
  id, name, slug, plan, status, created_at, updated_at

organization_memberships
  id, organization_id, supabase_user_id, role, status

organization_invitations
  id, organization_id, email, role, acceptance_token_hash, status

repositories
  id, organization_id, provider, provider_repository_id
  full_name, default_branch, visibility, status

repository_api_keys
  id, repository_id, name, token_prefix, secret_hash
  scopes, allowed_branches, expires_at, revoked_at

policy_defaults
  organization_id, schema_version, defaults_json

repository_configs
  repository_id, schema_version, config_json

repository_policies
  id, repository_id, policy_type, target_type, target_selector
  config_json, status, priority

repository_gates
  id, repository_id, gate_type, metric, threshold, max_drop
  blocking, config_json, status

repository_components / owner_rules / package_nodes
  Repository ownership and context data used by reports and policies

coverage_debt
  id, repository_id, file_path, risk, reason, owner, status, metadata_json

coverage_reports / test_runs / gate_evaluations / pr_diff_coverage
  Read models produced by coverage-analysis and exposed here

repository_badge_settings
  repository_id, enabled, branch, metric, label, thresholds_json, token_hash
```

## APIs

Auth and organizations:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/auth/me` | Current user and visible orgs |
| `GET` | `/api/v1/orgs` | List orgs |
| `POST` | `/api/v1/orgs` | Create org |
| `GET` | `/api/v1/orgs/{org_id}` | Read org |
| `PATCH` | `/api/v1/orgs/{org_id}` | Update org |
| `GET` | `/api/v1/orgs/{org_id}/memberships` | List members |
| `POST` | `/api/v1/orgs/{org_id}/memberships` | Add member directly |
| `PATCH` | `/api/v1/orgs/{org_id}/memberships/{membership_id}` | Update member |
| `GET` | `/api/v1/orgs/{org_id}/invitations` | List invitations |
| `POST` | `/api/v1/orgs/{org_id}/invitations` | Invite by email |
| `POST` | `/api/v1/orgs/{org_id}/invitations/{invitation_id}/accept` | Accept invitation |

Repositories and policy:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/repositories` | List repositories |
| `POST` | `/api/v1/orgs/{org_id}/repositories` | Register repository |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}` | Read repository |
| `PATCH` | `/api/v1/orgs/{org_id}/repositories/{repository_id}` | Update repository |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config` | Effective config |
| `PUT` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config` | Save override |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config/validate` | Validate config |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates` | List gates |
| `PUT` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates` | Replace gates |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/api-keys` | List API keys |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/api-keys` | Create API key |

Reports, dashboards, badges, and debt:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/commits/{sha}/report` | Commit report |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/pull-requests/{number}/report` | PR report |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/commits/{sha}/test-runs` | Test runs |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/trends` | Coverage trends |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gate-evaluations` | Gate history |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/dashboard` | Repository dashboard |
| `GET` | `/api/v1/orgs/{org_id}/dashboard` | Organization dashboard |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt` | List debt |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt` | Create debt |
| `PATCH` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt/{debt_id}` | Update debt |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/badges/coverage.svg` | Coverage badge SVG |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/badges/coverage.json` | Coverage badge JSON |

Internal:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/authz/check` | Service authorization decision |
| `GET` | `/internal/v1/control-plane/repositories/{repository_id}/coverage-context` | Coverage ownership context |
| `GET` | `/internal/v1/orgs/{org_id}/repositories/{repository_id}/effective-config` | Effective repository config |

## Source Map

```text
api/
  Public and internal resources, request records, response records, badge SVG

application/
  Organization, membership, repository, config, policy, gates, dashboard,
  coverage debt, badge, report read, and authz behavior

adapter/auth/
  Supabase JWT user resolution

adapter/jdbc/
  JDBC repository and schema-oriented persistence

domain/
  Authenticated user and auth context records

config/
  Helidon/CDI wiring
```

## Tests

```text
src/test/resources/features/organization
  BDD coverage for organization management, repository API keys, and coverage
  debt lifecycle

src/test/java/dev/vericov/organization/api
  Resource and integration-style API tests with in-memory service state

src/test/java/dev/vericov/organization/application
  Application service and repository context behavior

src/test/java/dev/vericov/organization/adapter
  Supabase JWT and JDBC schema tests
```

Run this service only:

```bash
mvn -pl services/organization test
```
