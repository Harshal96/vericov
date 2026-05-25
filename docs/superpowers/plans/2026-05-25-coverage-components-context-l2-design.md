# Coverage Components and Repository Context L2 Design

Date: 2026-05-25
Status: Proposed
Owner: Organization service, Coverage Analysis service

## Current State

The product and backend docs describe components, package rollups, and owner-aware policy, but the implementation has only draft surfaces. `repository_policies` and `repository_gate_configurations` can target `component` and `path`, uploads can carry a free-form `component`, and Integrations Config allows `component` as a scope type. There is no canonical `components` table in `infra/supabase/volumes/db/vericov.sql`, no component CRUD in the Organization service, no CODEOWNERS parser, no ownership resolution, no package graph model, and no component coverage rollups. Integrations currently rejects component scopes because canonical component ownership does not exist.

## Goals

- Add a canonical repository component model owned by the Organization service.
- Resolve repository-relative paths to components and owners deterministically.
- Support manual component configuration, CODEOWNERS-derived ownership, and upload-provided component hints.
- Store package graph metadata in a form that can support monorepo rollups without requiring a full build graph in v1.
- Provide Coverage Analysis with a versioned repository context snapshot for each report/gate evaluation.
- Make component scope validation possible for Integrations Config.

## Non-Goals

- Do not store source text in SaaS context records.
- Do not require language-specific build graph extraction for the first implementation.
- Do not make Git Integration decide policy, ownership, coverage risk, or gate outcomes.
- Do not make upload-provided component strings canonical unless they match an active component or policy allows provisional components.

## Service Ownership

- Organization service owns component CRUD, ownership override rules, package metadata, and the internal coverage context read API.
- Coverage Analysis consumes a context snapshot to assign component IDs, owners, criticality labels, and package metadata to coverage files, gap findings, risk scores, and gates.
- Git Integration fetches provider metadata such as CODEOWNERS and repository files through service-authenticated internal APIs.
- Integrations Config validates `component` bindings by calling Control Plane scope validation once canonical components exist.

## Data Model

Add `vericov.components`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `name` | text | Unique display/key name per repository |
| `description` | text | Optional human description |
| `path_patterns` | text[] | Repository-relative glob patterns owned by component |
| `owners` | text[] | Vericov owner keys, Git provider teams, or CODEOWNERS identities |
| `criticality` | text | `critical`, `high`, `medium`, `low`; default `medium` |
| `metadata_json` | jsonb | Package graph hints, labels, language, runtime, service tier |
| `status` | text | `active`, `disabled` |
| `created_by_user_id` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

Add `vericov.repository_owner_rules`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `source` | text | `manual`, `codeowners`, `component` |
| `pattern` | text | Repository-relative path pattern |
| `owners` | text[] | Resolved owner keys |
| `priority` | integer | Lower number wins before source-specific order |
| `source_ref` | text | Commit SHA or config version reference |
| `status` | text | `active`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

Add `vericov.repository_package_nodes`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `component_id` | uuid | Nullable FK to components |
| `package_name` | text | Package/workspace/module name |
| `package_path` | text | Repository-relative root |
| `manifest_path` | text | Source manifest path |
| `ecosystem` | text | `npm`, `maven`, `gradle`, `python`, `go`, `rust`, `unknown` |
| `metadata_json` | jsonb | Version, labels, dependency summary |
| `status` | text | `active`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

Add `vericov.component_coverage_rollups`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `coverage_report_id` | uuid | FK to coverage reports |
| `component_id` | uuid | FK to components |
| `owner` | text | Primary owner used for rollup filters |
| coverage counters | integer | Line, branch, function, statement covered/total |
| `gap_count` | integer | Unsuppressed active gap count |
| `debt_count` | integer | Active debt count |
| `risk_score_total` | numeric | Sum of active finding risk scores |
| `created_at` | timestamptz | Created time |

`coverage_file_summaries` should gain nullable `component_id`, `package_name`, and `owners` columns in the implementation L3 plan. The L2 decision is that file-level projection rows carry resolved context for fast report reads.

## Path and Owner Resolution

Resolution order:

1. Exact manual owner/component overrides.
2. Active component `path_patterns`, sorted by longest literal prefix, then lowest priority, then component name.
3. CODEOWNERS rules in provider order, where later matching CODEOWNERS lines override earlier lines.
4. Upload-provided component/package hints, only if no canonical component matched.
5. Repository fallback owner from policy defaults or `unowned`.

When multiple owners remain after resolution, keep all owners in evidence and choose the first as `primary_owner` only for sorting and rollups. Store the full list in JSON evidence so UI and agents do not lose team context.

## APIs and Events

Public Control Plane endpoints:

- `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/components`
- `POST /api/v1/orgs/{org_id}/repositories/{repository_id}/components`
- `PATCH /api/v1/orgs/{org_id}/repositories/{repository_id}/components/{component_id}`
- `POST /api/v1/orgs/{org_id}/repositories/{repository_id}/components/resolve-preview`

Internal Control Plane endpoint:

- `GET /internal/v1/control-plane/repositories/{repository_id}/coverage-context?commit_sha={sha}`

The internal response returns `context_version`, components, owner rules, package nodes, policy defaults relevant to context, and generated timestamp. Coverage Analysis persists the `context_version` in finding and gate evidence.

Events:

- `repository.components.updated`: emitted after component CRUD changes.
- `repository.context.synced`: emitted after CODEOWNERS or package metadata refresh.
- `repository.context.sync_failed`: emitted when provider metadata cannot be parsed or fetched.

## Processing Flow

1. Organization admins define components or import component config from `vericov.yml`.
2. Git Integration fetches CODEOWNERS and manifest metadata when repository context sync is requested.
3. Organization service parses CODEOWNERS into owner rules and stores package nodes.
4. Coverage Analysis starts a report and loads a context snapshot by repository and commit.
5. Every file summary is resolved to component, package, owner list, and criticality.
6. Component rollups are calculated from file summaries and later enriched with gap/debt counts.
7. Gate evaluation and risk scoring use the same resolved context snapshot for deterministic replay.

## Privacy and Security

- Store paths, patterns, owner keys, and manifest metadata only; do not store source file contents.
- Treat CODEOWNERS and package metadata as tenant-private.
- Validate path patterns at API boundaries to prevent absolute paths, parent traversal, or unbounded regex behavior.
- Internal context reads require service token auth and return only repository-scoped metadata.

## Tests

- Unit tests for path glob validation and matching precedence.
- CODEOWNERS parser tests for comments, escaped spaces, later-rule override, team owners, and malformed lines.
- Organization service tests for component CRUD, admin authorization, viewer read-only behavior, and resolve preview.
- JDBC schema tests for uniqueness, FKs, tenant scoping, and disabled component exclusion.
- Coverage Analysis tests proving file summaries and component rollups use a fixed context snapshot.
- Integrations Config test proving component scope validation succeeds only for active repository-owned components.

## Rollout Order

1. Add component and owner-rule schema plus Organization repository/application/API models.
2. Add manual component CRUD and resolve preview.
3. Add internal coverage context endpoint.
4. Teach Coverage Analysis to persist component and owner projections on file summaries.
5. Add component coverage rollups.
6. Add CODEOWNERS and package metadata sync.
7. Enable component scope validation in Integrations Config.

## Open Follow-up Questions

- Should upload-provided component hints create provisional components, or should they only tag reports until an admin maps them?
- Should CODEOWNERS identities be normalized to Vericov teams in v1, or displayed as provider-native owner keys?
- Which package ecosystems are in the first package-node sync: npm, Maven/Gradle, Python, Go, or all lightweight manifest formats?
