CREATE SCHEMA IF NOT EXISTS vericov;

REVOKE ALL ON SCHEMA vericov FROM PUBLIC;
REVOKE ALL ON SCHEMA vericov FROM anon;
REVOKE ALL ON SCHEMA vericov FROM authenticated;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pgmq;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pgmq.list_queues()
        WHERE queue_name = 'coverage_analysis_jobs'
    ) THEN
        PERFORM pgmq.create('coverage_analysis_jobs');
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pgmq.list_queues()
        WHERE queue_name = 'coverage_analysis_dead_letters'
    ) THEN
        PERFORM pgmq.create('coverage_analysis_dead_letters');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS vericov.tenants (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    name text NOT NULL,
    slug text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.organizations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL UNIQUE REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    name text NOT NULL,
    slug text NOT NULL UNIQUE,
    plan text NOT NULL DEFAULT 'free'
        CHECK (plan IN ('free', 'team', 'enterprise')),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'suspended', 'deleted')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (length(trim(name)) BETWEEN 1 AND 120),
    CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$')
);

CREATE TABLE IF NOT EXISTS vericov.memberships (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    supabase_user_id uuid NOT NULL,
    role text NOT NULL CHECK (role IN ('owner', 'admin', 'developer', 'viewer', 'auditor')),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'invited', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, supabase_user_id)
);

CREATE TABLE IF NOT EXISTS vericov.organization_invitations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    email text NOT NULL,
    role text NOT NULL CHECK (role IN ('owner', 'admin', 'developer', 'viewer', 'auditor')),
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'accepted', 'expired', 'revoked')),
    invited_by_user_id uuid NOT NULL,
    acceptance_token_hash text NOT NULL CHECK (length(acceptance_token_hash) = 64),
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (email = lower(trim(email))),
    CHECK (length(email) BETWEEN 3 AND 320)
);

CREATE TABLE IF NOT EXISTS vericov.repositories (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    provider text NOT NULL,
    provider_repository_id text NOT NULL,
    full_name text NOT NULL,
    default_branch text NOT NULL DEFAULT 'main',
    visibility text NOT NULL DEFAULT 'private'
        CHECK (visibility IN ('public', 'private', 'internal')),
    privacy_mode text NOT NULL DEFAULT 'private_saas'
        CHECK (privacy_mode IN ('public_saas', 'private_saas', 'metadata_only', 'self_hosted')),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled', 'archived')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (provider IN ('github', 'gitlab', 'bitbucket')),
    CHECK (length(trim(provider_repository_id)) BETWEEN 1 AND 120),
    CHECK (length(trim(full_name)) BETWEEN 1 AND 300),
    CHECK (length(trim(default_branch)) BETWEEN 1 AND 255),
    UNIQUE (tenant_id, provider, provider_repository_id)
);

CREATE TABLE IF NOT EXISTS vericov.organization_policy_defaults (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL UNIQUE REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    defaults_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    updated_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (jsonb_typeof(defaults_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.repository_configs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    source text NOT NULL DEFAULT 'ui_override'
        CHECK (source IN ('ui_override', 'repo_file', 'computed')),
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    validation_status text NOT NULL DEFAULT 'valid'
        CHECK (validation_status IN ('valid', 'invalid', 'warning')),
    validation_errors jsonb NOT NULL DEFAULT '[]'::jsonb,
    updated_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, repository_id, source),
    CHECK (jsonb_typeof(config_json) = 'object'),
    CHECK (jsonb_typeof(validation_errors) = 'array')
);

CREATE TABLE IF NOT EXISTS vericov.repository_policies (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    description text,
    policy_type text NOT NULL CHECK (policy_type IN ('coverage', 'mutation', 'agent_review', 'waiver')),
    target_type text NOT NULL CHECK (target_type IN ('repository', 'component', 'path')),
    target_selector text,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    priority integer NOT NULL DEFAULT 100 CHECK (priority BETWEEN 0 AND 1000),
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, repository_id, name),
    CHECK (jsonb_typeof(config_json) = 'object'),
    CHECK (
        target_type = 'repository'
        OR length(trim(coalesce(target_selector, ''))) > 0
    )
);

CREATE TABLE IF NOT EXISTS vericov.repository_gate_configurations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    gate_type text NOT NULL CHECK (
        gate_type IN (
            'project_coverage',
            'patch_coverage',
            'coverage_drop',
            'component_coverage',
            'mutation_score',
            'agent_review_required'
        )
    ),
    metric text NOT NULL CHECK (metric IN ('line', 'branch', 'function', 'statement', 'mutation', 'risk')),
    threshold numeric(12, 4) CHECK (threshold IS NULL OR threshold >= 0),
    max_drop numeric(12, 4) CHECK (max_drop IS NULL OR max_drop >= 0),
    blocking boolean NOT NULL DEFAULT true,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, repository_id, name),
    CHECK (jsonb_typeof(config_json) = 'object'),
    CHECK (gate_type = 'agent_review_required' OR threshold IS NOT NULL),
    CHECK (gate_type = 'coverage_drop' OR max_drop IS NULL)
);

CREATE TABLE IF NOT EXISTS vericov.repository_badge_settings (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    enabled boolean NOT NULL DEFAULT false,
    branch text NOT NULL DEFAULT 'main',
    metric text NOT NULL DEFAULT 'line'
        CHECK (metric IN ('line', 'branch', 'function', 'statement')),
    label text NOT NULL DEFAULT 'coverage'
        CHECK (length(trim(label)) BETWEEN 1 AND 64),
    thresholds_json jsonb NOT NULL DEFAULT '{"brightgreen":90,"green":80,"yellow":60}'::jsonb,
    token_hash text,
    token_prefix text,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    UNIQUE (org_id, repository_id),
    CHECK (length(trim(branch)) BETWEEN 1 AND 255),
    CHECK (jsonb_typeof(thresholds_json) = 'object'),
    CHECK ((token_hash IS NULL AND token_prefix IS NULL) OR (token_hash IS NOT NULL AND token_prefix IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS vericov.repository_api_keys (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    key_prefix text NOT NULL,
    key_hash text NOT NULL CHECK (length(key_hash) = 64),
    scopes text[] NOT NULL DEFAULT ARRAY['uploads:create', 'uploads:read'],
    branch_allow_patterns text[] NOT NULL DEFAULT ARRAY['*'],
    expires_at timestamptz,
    revoked_at timestamptz,
    created_by_user_id uuid NOT NULL,
    revoked_by_user_id uuid,
    last_used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (repository_id, key_prefix),
    CHECK (cardinality(scopes) > 0),
    CHECK (cardinality(branch_allow_patterns) > 0)
);

CREATE TABLE IF NOT EXISTS vericov.repository_ci_trusts (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider text NOT NULL CHECK (provider IN ('github_actions')),
    issuer text NOT NULL,
    audience text NOT NULL,
    subject_pattern text NOT NULL,
    scopes text[] NOT NULL DEFAULT ARRAY['uploads:create', 'uploads:read'],
    branch_allow_patterns text[] NOT NULL DEFAULT ARRAY['*'],
    expires_at timestamptz,
    revoked_at timestamptz,
    created_by_user_id uuid NOT NULL,
    revoked_by_user_id uuid,
    last_used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (repository_id, provider, subject_pattern),
    CHECK (issuer = 'https://token.actions.githubusercontent.com'),
    CHECK (length(trim(audience)) BETWEEN 1 AND 255),
    CHECK (length(trim(subject_pattern)) BETWEEN 1 AND 500),
    CHECK (cardinality(scopes) > 0),
    CHECK (cardinality(branch_allow_patterns) > 0)
);

CREATE TABLE IF NOT EXISTS vericov.runner_upload_tokens (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    source_type text NOT NULL CHECK (source_type IN ('repository_api_key', 'github_actions_oidc')),
    source_id uuid,
    token_id text NOT NULL UNIQUE,
    scopes text[] NOT NULL DEFAULT ARRAY['uploads:create'],
    branch_allow_patterns text[] NOT NULL DEFAULT ARRAY['*'],
    issued_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    last_used_at timestamptz,
    CHECK (expires_at > issued_at),
    CHECK (cardinality(scopes) > 0),
    CHECK (cardinality(branch_allow_patterns) > 0)
);

ALTER TABLE IF EXISTS vericov.repository_api_keys
    ADD COLUMN IF NOT EXISTS created_by_user_id uuid,
    ADD COLUMN IF NOT EXISTS revoked_by_user_id uuid,
    ADD COLUMN IF NOT EXISTS last_used_at timestamptz,
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS vericov.uploads (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    api_key_id uuid REFERENCES vericov.repository_api_keys (id) ON DELETE SET NULL,
    commit_sha text NOT NULL,
    branch text NOT NULL,
    pull_request_number integer,
    ci_provider text,
    ci_build_id text,
    ci_build_url text,
    flags text[] NOT NULL DEFAULT ARRAY[]::text[],
    component text,
    package_name text,
    status text NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'processing', 'processed', 'failed', 'rejected')),
    idempotency_key text NOT NULL,
    accepted_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    error_code text,
    error_message text,
    UNIQUE (repository_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS vericov.upload_artifacts (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    upload_id uuid NOT NULL REFERENCES vericov.uploads (id) ON DELETE CASCADE,
    name text NOT NULL,
    kind text NOT NULL CHECK (kind IN ('coverage', 'test_results', 'metadata')),
    format text NOT NULL,
    content_type text NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
    storage_bucket text NOT NULL,
    storage_path text NOT NULL,
    sha256_hex text NOT NULL CHECK (length(sha256_hex) = 64),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (upload_id, name)
);

CREATE TABLE IF NOT EXISTS vericov.analysis_jobs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    upload_id uuid NOT NULL UNIQUE REFERENCES vericov.uploads (id) ON DELETE CASCADE,
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    commit_sha text NOT NULL,
    status text NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
    priority integer NOT NULL DEFAULT 100,
    queue_name text NOT NULL DEFAULT 'coverage_analysis_jobs',
    queue_message_id bigint,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts integer NOT NULL DEFAULT 5 CHECK (max_attempts > 0),
    run_after timestamptz NOT NULL DEFAULT now(),
    queued_at timestamptz NOT NULL DEFAULT now(),
    locked_by text,
    locked_at timestamptz,
    lease_expires_at timestamptz,
    started_at timestamptz,
    finished_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.analysis_job_attempts (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    analysis_job_id uuid NOT NULL REFERENCES vericov.analysis_jobs (id) ON DELETE CASCADE,
    worker_id text NOT NULL,
    attempt_number integer NOT NULL CHECK (attempt_number > 0),
    status text NOT NULL CHECK (status IN ('succeeded', 'failed')),
    started_at timestamptz NOT NULL,
    finished_at timestamptz NOT NULL,
    error_code text,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.coverage_reports (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    upload_id uuid NOT NULL UNIQUE REFERENCES vericov.uploads (id) ON DELETE CASCADE,
    commit_sha text NOT NULL,
    branch text NOT NULL,
    pull_request_number integer,
    status text NOT NULL DEFAULT 'complete'
        CHECK (status IN ('processing', 'complete', 'failed')),
    line_covered integer NOT NULL CHECK (line_covered >= 0),
    line_total integer NOT NULL CHECK (line_total >= 0),
    branch_covered integer NOT NULL CHECK (branch_covered >= 0),
    branch_total integer NOT NULL CHECK (branch_total >= 0),
    function_covered integer NOT NULL CHECK (function_covered >= 0),
    function_total integer NOT NULL CHECK (function_total >= 0),
    statement_covered integer NOT NULL CHECK (statement_covered >= 0),
    statement_total integer NOT NULL CHECK (statement_total >= 0),
    normalized_storage_bucket text,
    normalized_storage_path text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (line_covered <= line_total),
    CHECK (branch_covered <= branch_total),
    CHECK (function_covered <= function_total),
    CHECK (statement_covered <= statement_total)
);

CREATE TABLE IF NOT EXISTS vericov.badge_cache (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    badge_type text NOT NULL DEFAULT 'coverage' CHECK (badge_type = 'coverage'),
    cache_scope text NOT NULL CHECK (cache_scope IN ('token', 'authenticated')),
    branch text NOT NULL,
    metric text NOT NULL CHECK (metric IN ('line', 'branch', 'function', 'statement')),
    label text NOT NULL CHECK (length(trim(label)) BETWEEN 1 AND 64),
    message text NOT NULL CHECK (length(trim(message)) BETWEEN 1 AND 64),
    color text NOT NULL CHECK (length(trim(color)) BETWEEN 1 AND 32),
    commit_sha text,
    coverage_percent numeric(12, 4),
    source_report_id uuid REFERENCES vericov.coverage_reports (id) ON DELETE SET NULL,
    report_created_at timestamptz,
    settings_updated_at timestamptz NOT NULL,
    cached_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    UNIQUE (org_id, repository_id, badge_type, cache_scope, branch, metric),
    CHECK (length(trim(branch)) BETWEEN 1 AND 255),
    CHECK (commit_sha IS NULL OR length(trim(commit_sha)) BETWEEN 1 AND 128),
    CHECK (coverage_percent IS NULL OR (coverage_percent >= 0 AND coverage_percent <= 100)),
    CHECK (expires_at > cached_at)
);

CREATE TABLE IF NOT EXISTS vericov.coverage_file_summaries (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    commit_sha text NOT NULL,
    file_path text NOT NULL,
    line_covered integer NOT NULL CHECK (line_covered >= 0),
    line_total integer NOT NULL CHECK (line_total >= 0),
    branch_covered integer NOT NULL CHECK (branch_covered >= 0),
    branch_total integer NOT NULL CHECK (branch_total >= 0),
    function_covered integer NOT NULL CHECK (function_covered >= 0),
    function_total integer NOT NULL CHECK (function_total >= 0),
    statement_covered integer NOT NULL CHECK (statement_covered >= 0),
    statement_total integer NOT NULL CHECK (statement_total >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (coverage_report_id, file_path),
    CHECK (line_covered <= line_total),
    CHECK (branch_covered <= branch_total),
    CHECK (function_covered <= function_total),
    CHECK (statement_covered <= statement_total)
);

CREATE TABLE IF NOT EXISTS vericov.coverage_line_hits (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    commit_sha text NOT NULL,
    file_path text NOT NULL,
    line_number integer NOT NULL CHECK (line_number > 0),
    hits bigint NOT NULL CHECK (hits >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (coverage_report_id, file_path, line_number)
);

CREATE TABLE IF NOT EXISTS vericov.test_runs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    upload_id uuid NOT NULL REFERENCES vericov.uploads (id) ON DELETE CASCADE,
    upload_artifact_id uuid NOT NULL REFERENCES vericov.upload_artifacts (id) ON DELETE CASCADE,
    commit_sha text NOT NULL,
    branch text NOT NULL,
    pull_request_number integer,
    suite_name text NOT NULL CHECK (length(trim(suite_name)) BETWEEN 1 AND 512),
    suite_index integer NOT NULL CHECK (suite_index >= 0),
    status text NOT NULL CHECK (status IN ('passed', 'failed', 'error', 'skipped')),
    total_count integer NOT NULL CHECK (total_count >= 0),
    passed_count integer NOT NULL CHECK (passed_count >= 0),
    failed_count integer NOT NULL CHECK (failed_count >= 0),
    error_count integer NOT NULL CHECK (error_count >= 0),
    skipped_count integer NOT NULL CHECK (skipped_count >= 0),
    duration_ms bigint NOT NULL CHECK (duration_ms >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (upload_id, upload_artifact_id, suite_index),
    CHECK (passed_count + failed_count + error_count + skipped_count = total_count)
);

CREATE TABLE IF NOT EXISTS vericov.pull_request_coverage_diffs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL UNIQUE REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    pull_request_number integer NOT NULL CHECK (pull_request_number > 0),
    provider_key text NOT NULL,
    base_sha text NOT NULL,
    head_sha text NOT NULL,
    status text NOT NULL CHECK (status IN ('complete', 'base_coverage_missing', 'unavailable')),
    patch_line_covered integer NOT NULL CHECK (patch_line_covered >= 0),
    patch_line_total integer NOT NULL CHECK (patch_line_total >= 0),
    newly_missed_line_count integer NOT NULL CHECK (newly_missed_line_count >= 0),
    lost_coverage_line_count integer NOT NULL CHECK (lost_coverage_line_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (patch_line_covered <= patch_line_total)
);

CREATE TABLE IF NOT EXISTS vericov.pull_request_coverage_diff_files (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    pr_diff_id uuid NOT NULL REFERENCES vericov.pull_request_coverage_diffs (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    file_path text NOT NULL,
    old_file_path text,
    change_status text NOT NULL,
    patch_line_covered integer NOT NULL CHECK (patch_line_covered >= 0),
    patch_line_total integer NOT NULL CHECK (patch_line_total >= 0),
    newly_missed_line_count integer NOT NULL CHECK (newly_missed_line_count >= 0),
    lost_coverage_line_count integer NOT NULL CHECK (lost_coverage_line_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (patch_line_covered <= patch_line_total)
);

CREATE TABLE IF NOT EXISTS vericov.pull_request_coverage_diff_lines (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    pr_diff_id uuid NOT NULL REFERENCES vericov.pull_request_coverage_diffs (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    file_path text NOT NULL,
    old_file_path text,
    base_line_number integer CHECK (base_line_number IS NULL OR base_line_number > 0),
    head_line_number integer CHECK (head_line_number IS NULL OR head_line_number > 0),
    change_type text NOT NULL CHECK (change_type IN ('added', 'deleted', 'context')),
    executable boolean NOT NULL DEFAULT false,
    base_hits bigint,
    head_hits bigint,
    newly_missed boolean NOT NULL DEFAULT false,
    lost_coverage boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.gate_evaluations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    coverage_report_id uuid REFERENCES vericov.coverage_reports (id) ON DELETE SET NULL,
    commit_sha text NOT NULL,
    branch text NOT NULL,
    pull_request_number integer,
    gate_name text NOT NULL,
    gate_type text NOT NULL,
    metric text NOT NULL,
    threshold numeric(12, 4),
    actual numeric(12, 4),
    status text NOT NULL CHECK (status IN ('passed', 'failed', 'warning')),
    blocking boolean NOT NULL DEFAULT true,
    details_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    evaluated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (length(trim(gate_name)) BETWEEN 1 AND 120),
    CHECK (length(trim(commit_sha)) BETWEEN 1 AND 128),
    CHECK (length(trim(branch)) BETWEEN 1 AND 255),
    CHECK (pull_request_number IS NULL OR pull_request_number > 0),
    CHECK (jsonb_typeof(details_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.upload_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    upload_id uuid NOT NULL REFERENCES vericov.uploads (id) ON DELETE CASCADE,
    event_type text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.integration_providers (
    provider_key text PRIMARY KEY,
    integration_type text NOT NULL
        CHECK (integration_type IN ('git', 'chat', 'issue_tracker', 'ai')),
    display_name text NOT NULL,
    auth_strategy text NOT NULL
        CHECK (auth_strategy IN ('github_app', 'oauth_app', 'api_key')),
    capabilities text[] NOT NULL DEFAULT ARRAY[]::text[],
    default_config jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (provider_key ~ '^[a-z0-9]([a-z0-9_-]{0,62}[a-z0-9])?$'),
    CHECK (length(trim(display_name)) BETWEEN 1 AND 120),
    UNIQUE (provider_key, integration_type),
    CHECK (jsonb_typeof(default_config) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.integration_connections (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    integration_type text NOT NULL
        CHECK (integration_type IN ('git', 'chat', 'issue_tracker', 'ai')),
    display_name text NOT NULL,
    external_account_id text NOT NULL,
    external_account_name text,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('draft', 'active', 'needs_reauth', 'disabled', 'revoked')),
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by uuid NOT NULL,
    last_verified_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (length(trim(display_name)) BETWEEN 1 AND 120),
    CHECK (length(trim(external_account_id)) BETWEEN 1 AND 255),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, org_id, id),
    UNIQUE (tenant_id, org_id, id, provider_key),
    FOREIGN KEY (provider_key, integration_type)
        REFERENCES vericov.integration_providers (provider_key, integration_type)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CHECK (jsonb_typeof(config_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.integration_credentials (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL,
    credential_kind text NOT NULL
        CHECK (credential_kind IN (
            'oauth_access_token',
            'oauth_refresh_token',
            'github_app_private_key',
            'webhook_secret',
            'api_token'
        )),
    secret_ref text NOT NULL,
    key_version integer NOT NULL DEFAULT 1 CHECK (key_version > 0),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'rotating', 'revoked', 'expired')),
    expires_at timestamptz,
    last_rotated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, connection_id)
        REFERENCES vericov.integration_connections (tenant_id, id)
        ON DELETE CASCADE,
    CHECK (length(trim(secret_ref)) BETWEEN 1 AND 500)
);

CREATE TABLE IF NOT EXISTS vericov.integration_bindings (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL,
    scope_type text NOT NULL CHECK (scope_type IN ('organization', 'repository', 'component')),
    scope_id uuid NOT NULL,
    capabilities text[] NOT NULL DEFAULT ARRAY[]::text[],
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, connection_id, scope_type, scope_id),
    FOREIGN KEY (tenant_id, connection_id)
        REFERENCES vericov.integration_connections (tenant_id, id)
        ON DELETE CASCADE,
    CHECK (jsonb_typeof(config_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.integration_webhook_endpoints (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL,
    provider_key text NOT NULL REFERENCES vericov.integration_providers (provider_key) ON UPDATE CASCADE ON DELETE RESTRICT,
    external_webhook_id text,
    endpoint_url text NOT NULL,
    event_types text[] NOT NULL DEFAULT ARRAY[]::text[],
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled', 'error', 'deleted')),
    signing_secret_ref text NOT NULL,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_delivery_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_delivered_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, org_id, connection_id, provider_key, id),
    FOREIGN KEY (tenant_id, org_id, connection_id, provider_key)
        REFERENCES vericov.integration_connections (tenant_id, org_id, id, provider_key)
        ON DELETE CASCADE,
    CHECK (external_webhook_id IS NULL OR length(trim(external_webhook_id)) BETWEEN 1 AND 255),
    CHECK (length(trim(endpoint_url)) BETWEEN 1 AND 2000),
    CHECK (length(trim(signing_secret_ref)) BETWEEN 1 AND 500),
    CHECK (jsonb_typeof(config_json) = 'object'),
    CHECK (jsonb_typeof(last_delivery_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.integration_sync_states (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL,
    sync_type text NOT NULL,
    scope_type text NOT NULL CHECK (scope_type IN ('organization', 'repository', 'component')),
    scope_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'idle'
        CHECK (status IN ('idle', 'running', 'succeeded', 'failed', 'paused')),
    cursor_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    checkpoint_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_error_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_started_at timestamptz,
    last_completed_at timestamptz,
    next_run_at timestamptz,
    lease_expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, connection_id, scope_type, scope_id, sync_type),
    FOREIGN KEY (tenant_id, org_id, connection_id)
        REFERENCES vericov.integration_connections (tenant_id, org_id, id)
        ON DELETE CASCADE,
    CHECK (length(trim(sync_type)) BETWEEN 1 AND 120),
    CHECK (jsonb_typeof(cursor_json) = 'object'),
    CHECK (jsonb_typeof(checkpoint_json) = 'object'),
    CHECK (jsonb_typeof(last_error_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.integration_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid REFERENCES vericov.organizations (id) ON DELETE SET NULL,
    connection_id uuid,
    webhook_endpoint_id uuid,
    provider_key text NOT NULL REFERENCES vericov.integration_providers (provider_key) ON UPDATE CASCADE ON DELETE RESTRICT,
    event_type text NOT NULL,
    external_event_id text,
    scope_type text CHECK (scope_type IN ('organization', 'repository', 'component')),
    scope_id uuid,
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'processed', 'failed', 'ignored')),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, org_id, connection_id, provider_key)
        REFERENCES vericov.integration_connections (tenant_id, org_id, id, provider_key),
    FOREIGN KEY (tenant_id, org_id, connection_id, provider_key, webhook_endpoint_id)
        REFERENCES vericov.integration_webhook_endpoints (tenant_id, org_id, connection_id, provider_key, id),
    CHECK (length(trim(event_type)) BETWEEN 1 AND 160),
    CHECK (external_event_id IS NULL OR length(trim(external_event_id)) BETWEEN 1 AND 255),
    CHECK (connection_id IS NULL OR org_id IS NOT NULL),
    CHECK (webhook_endpoint_id IS NULL OR (org_id IS NOT NULL AND connection_id IS NOT NULL)),
    CHECK ((scope_type IS NULL AND scope_id IS NULL) OR (scope_type IS NOT NULL AND scope_id IS NOT NULL)),
    CHECK (jsonb_typeof(payload) = 'object'),
    CHECK (jsonb_typeof(error_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.git_webhook_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid REFERENCES vericov.organizations (id) ON DELETE SET NULL,
    repository_id uuid REFERENCES vericov.repositories (id) ON DELETE SET NULL,
    connection_id uuid,
    webhook_endpoint_id uuid,
    provider_key text NOT NULL REFERENCES vericov.integration_providers (provider_key) ON UPDATE CASCADE ON DELETE RESTRICT,
    event_type text NOT NULL,
    delivery_id text NOT NULL,
    signature_valid boolean NOT NULL DEFAULT false,
    payload_sha256 text NOT NULL CHECK (length(payload_sha256) = 64),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    normalized_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'received'
        CHECK (status IN ('received', 'processed', 'ignored', 'failed')),
    error_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider_key, delivery_id),
    CHECK (length(trim(event_type)) BETWEEN 1 AND 160),
    CHECK (length(trim(delivery_id)) BETWEEN 1 AND 255),
    CHECK (jsonb_typeof(payload) = 'object'),
    CHECK (jsonb_typeof(normalized_payload) = 'object'),
    CHECK (jsonb_typeof(error_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.git_pull_requests (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    provider_pull_request_id text NOT NULL,
    number integer NOT NULL CHECK (number > 0),
    title text NOT NULL,
    author text NOT NULL,
    base_branch text NOT NULL,
    base_sha text NOT NULL,
    head_branch text NOT NULL,
    head_sha text NOT NULL,
    state text NOT NULL CHECK (state IN ('open', 'closed', 'merged')),
    provider_url text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, repository_id, provider_key, provider_pull_request_id),
    UNIQUE (tenant_id, repository_id, provider_key, number)
);

CREATE TABLE IF NOT EXISTS vericov.git_check_runs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    commit_sha text NOT NULL,
    name text NOT NULL,
    provider_check_id text,
    status text NOT NULL CHECK (status IN ('queued', 'in_progress', 'completed')),
    conclusion text CHECK (conclusion IN ('success', 'failure', 'neutral', 'cancelled', 'skipped', 'timed_out', 'action_required')),
    details_url text,
    output_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, idempotency_key),
    CHECK (length(trim(commit_sha)) BETWEEN 1 AND 128),
    CHECK (length(trim(name)) BETWEEN 1 AND 120),
    CHECK (jsonb_typeof(output_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.git_pr_comments (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    pull_request_number integer NOT NULL CHECK (pull_request_number > 0),
    comment_key text NOT NULL,
    provider_comment_id text,
    body_hash text NOT NULL CHECK (length(body_hash) = 64),
    status text NOT NULL CHECK (status IN ('posted', 'updated', 'unchanged', 'deleted', 'failed')),
    provider_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, pull_request_number, comment_key)
);

CREATE TABLE IF NOT EXISTS vericov.git_pr_annotations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    pull_request_number integer NOT NULL CHECK (pull_request_number > 0),
    annotation_key text NOT NULL,
    provider_annotation_id text,
    path text NOT NULL,
    start_line integer NOT NULL CHECK (start_line > 0),
    end_line integer NOT NULL CHECK (end_line > 0),
    annotation_level text NOT NULL CHECK (annotation_level IN ('notice', 'warning', 'failure')),
    message_hash text NOT NULL CHECK (length(message_hash) = 64),
    status text NOT NULL CHECK (status IN ('posted', 'updated', 'unchanged', 'failed')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, pull_request_number, annotation_key),
    CHECK (end_line >= start_line)
);

CREATE TABLE IF NOT EXISTS vericov.git_branches (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    branch_name text NOT NULL,
    base_sha text NOT NULL,
    provider_ref text,
    status text NOT NULL CHECK (status IN ('created', 'already_exists', 'failed')),
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, idempotency_key),
    UNIQUE (tenant_id, repository_id, provider_key, branch_name)
);

CREATE TABLE IF NOT EXISTS vericov.coverage_debt_items (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    component_id uuid,
    source_gap_id uuid,
    source_report_id uuid REFERENCES vericov.coverage_reports (id) ON DELETE SET NULL,
    source_commit_sha text NOT NULL,
    pull_request_number integer CHECK (pull_request_number IS NULL OR pull_request_number > 0),
    target_type text NOT NULL CHECK (target_type IN ('line', 'range', 'file', 'function', 'branch', 'component')),
    file_path text,
    line_start integer CHECK (line_start IS NULL OR line_start > 0),
    line_end integer CHECK (line_end IS NULL OR line_end > 0),
    symbol_name text,
    risk_level text NOT NULL CHECK (risk_level IN ('critical', 'high', 'medium', 'low')),
    reason text NOT NULL,
    owner text NOT NULL,
    status text NOT NULL CHECK (status IN ('active', 'resolved', 'expired', 'revoked')),
    expires_at timestamptz NOT NULL,
    resolved_at timestamptz,
    resolved_by_user_id uuid,
    revoked_at timestamptz,
    revoked_by_user_id uuid,
    linked_issue_url text,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (target_type = 'component' AND file_path IS NULL AND line_start IS NULL AND line_end IS NULL) OR
        (target_type != 'component' AND file_path IS NOT NULL)
    ),
    CHECK (
        (target_type IN ('line', 'range') AND line_start IS NOT NULL) OR
        (target_type NOT IN ('line', 'range') AND line_start IS NULL)
    ),
    CHECK (
        (target_type = 'range' AND line_end IS NOT NULL) OR
        (target_type != 'range' AND line_end IS NULL)
    ),
    CHECK (line_end IS NULL OR line_start IS NULL OR line_end >= line_start),
    CHECK (length(trim(reason)) BETWEEN 10 AND 2000),
    CHECK (length(trim(owner)) > 0),
    CHECK (jsonb_typeof(metadata_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.coverage_debt_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    debt_item_id uuid NOT NULL REFERENCES vericov.coverage_debt_items (id) ON DELETE CASCADE,
    event_type text NOT NULL CHECK (event_type IN ('created', 'updated', 'resolved', 'expired', 'revoked', 'matched_gap')),
    actor_user_id uuid,
    payload_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (jsonb_typeof(payload_json) = 'object')
);

CREATE INDEX IF NOT EXISTS coverage_debt_items_repository_status_idx
    ON vericov.coverage_debt_items (repository_id, status);

CREATE INDEX IF NOT EXISTS coverage_debt_events_debt_item_idx
    ON vericov.coverage_debt_events (debt_item_id);

CREATE INDEX IF NOT EXISTS repositories_tenant_idx
    ON vericov.repositories (tenant_id);

CREATE INDEX IF NOT EXISTS repositories_org_status_idx
    ON vericov.repositories (org_id, status, full_name);

CREATE INDEX IF NOT EXISTS repository_configs_repository_source_idx
    ON vericov.repository_configs (repository_id, source);

CREATE INDEX IF NOT EXISTS repository_policies_repository_status_priority_idx
    ON vericov.repository_policies (repository_id, status, priority);

CREATE INDEX IF NOT EXISTS repository_gate_configurations_repository_status_idx
    ON vericov.repository_gate_configurations (repository_id, status);

CREATE INDEX IF NOT EXISTS repository_badge_settings_repository_idx
    ON vericov.repository_badge_settings (repository_id);

CREATE INDEX IF NOT EXISTS repository_badge_settings_token_prefix_idx
    ON vericov.repository_badge_settings (token_prefix)
    WHERE token_prefix IS NOT NULL;

CREATE INDEX IF NOT EXISTS organizations_tenant_idx
    ON vericov.organizations (tenant_id);

CREATE INDEX IF NOT EXISTS memberships_user_status_idx
    ON vericov.memberships (supabase_user_id, status);

CREATE INDEX IF NOT EXISTS memberships_org_idx
    ON vericov.memberships (org_id);

CREATE INDEX IF NOT EXISTS organization_invitations_org_idx
    ON vericov.organization_invitations (org_id, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS organization_invitations_org_email_pending_idx
    ON vericov.organization_invitations (org_id, email)
    WHERE status = 'pending';

CREATE INDEX IF NOT EXISTS repository_api_keys_repository_active_idx
    ON vericov.repository_api_keys (repository_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS repository_api_keys_prefix_active_idx
    ON vericov.repository_api_keys (key_prefix)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS repository_ci_trusts_repository_active_idx
    ON vericov.repository_ci_trusts (repository_id, provider)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS runner_upload_tokens_token_active_idx
    ON vericov.runner_upload_tokens (token_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS uploads_repository_commit_idx
    ON vericov.uploads (repository_id, commit_sha);

CREATE INDEX IF NOT EXISTS uploads_status_accepted_idx
    ON vericov.uploads (status, accepted_at);

CREATE INDEX IF NOT EXISTS upload_artifacts_upload_idx
    ON vericov.upload_artifacts (upload_id);

CREATE INDEX IF NOT EXISTS analysis_jobs_status_run_after_priority_idx
    ON vericov.analysis_jobs (status, run_after, priority, queued_at);

CREATE INDEX IF NOT EXISTS analysis_jobs_upload_idx
    ON vericov.analysis_jobs (upload_id);

CREATE INDEX IF NOT EXISTS analysis_job_attempts_job_idx
    ON vericov.analysis_job_attempts (analysis_job_id, attempt_number);

CREATE INDEX IF NOT EXISTS coverage_reports_repository_commit_idx
    ON vericov.coverage_reports (repository_id, commit_sha);

CREATE INDEX IF NOT EXISTS coverage_reports_repository_branch_created_idx
    ON vericov.coverage_reports (repository_id, branch, created_at DESC);

CREATE INDEX IF NOT EXISTS coverage_reports_upload_idx
    ON vericov.coverage_reports (upload_id);

CREATE INDEX IF NOT EXISTS badge_cache_expires_at_idx
    ON vericov.badge_cache (expires_at);

CREATE INDEX IF NOT EXISTS coverage_file_summaries_report_idx
    ON vericov.coverage_file_summaries (coverage_report_id);

CREATE INDEX IF NOT EXISTS coverage_file_summaries_repository_file_idx
    ON vericov.coverage_file_summaries (repository_id, file_path);

CREATE INDEX IF NOT EXISTS coverage_line_hits_report_file_idx
    ON vericov.coverage_line_hits (coverage_report_id, file_path, line_number);

CREATE INDEX IF NOT EXISTS coverage_line_hits_repository_commit_file_idx
    ON vericov.coverage_line_hits (repository_id, commit_sha, file_path, line_number);

CREATE INDEX IF NOT EXISTS test_runs_upload_idx
    ON vericov.test_runs (upload_id, suite_index);

CREATE INDEX IF NOT EXISTS test_runs_repository_commit_idx
    ON vericov.test_runs (repository_id, commit_sha, created_at DESC);

CREATE INDEX IF NOT EXISTS test_runs_repository_branch_created_idx
    ON vericov.test_runs (repository_id, branch, created_at DESC);

CREATE INDEX IF NOT EXISTS pr_coverage_diffs_repository_pr_idx
    ON vericov.pull_request_coverage_diffs (repository_id, pull_request_number, created_at DESC);

CREATE INDEX IF NOT EXISTS pr_coverage_diff_files_diff_idx
    ON vericov.pull_request_coverage_diff_files (pr_diff_id, file_path);

CREATE INDEX IF NOT EXISTS pr_coverage_diff_lines_diff_file_idx
    ON vericov.pull_request_coverage_diff_lines (pr_diff_id, file_path, head_line_number);

CREATE INDEX IF NOT EXISTS gate_evaluations_repository_branch_evaluated_idx
    ON vericov.gate_evaluations (repository_id, branch, evaluated_at DESC);

CREATE INDEX IF NOT EXISTS gate_evaluations_repository_pr_evaluated_idx
    ON vericov.gate_evaluations (repository_id, pull_request_number, evaluated_at DESC)
    WHERE pull_request_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS gate_evaluations_report_idx
    ON vericov.gate_evaluations (coverage_report_id);

CREATE INDEX IF NOT EXISTS upload_events_upload_created_idx
    ON vericov.upload_events (upload_id, created_at);

CREATE INDEX IF NOT EXISTS integration_providers_type_status_idx
    ON vericov.integration_providers (integration_type, status, provider_key);

CREATE UNIQUE INDEX IF NOT EXISTS integration_connections_active_account_idx
    ON vericov.integration_connections (tenant_id, org_id, provider_key, external_account_id)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS integration_connections_tenant_provider_status_idx
    ON vericov.integration_connections (tenant_id, provider_key, status);

CREATE INDEX IF NOT EXISTS integration_connections_org_status_idx
    ON vericov.integration_connections (org_id, status, provider_key);

CREATE UNIQUE INDEX IF NOT EXISTS integration_credentials_active_kind_idx
    ON vericov.integration_credentials (tenant_id, connection_id, credential_kind)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS integration_credentials_connection_status_idx
    ON vericov.integration_credentials (connection_id, status, credential_kind);

CREATE INDEX IF NOT EXISTS integration_bindings_scope_status_idx
    ON vericov.integration_bindings (tenant_id, scope_type, scope_id, status);

CREATE INDEX IF NOT EXISTS integration_bindings_connection_status_idx
    ON vericov.integration_bindings (connection_id, status);

CREATE INDEX IF NOT EXISTS integration_webhook_endpoints_provider_status_idx
    ON vericov.integration_webhook_endpoints (tenant_id, provider_key, status);

CREATE INDEX IF NOT EXISTS integration_webhook_endpoints_connection_status_idx
    ON vericov.integration_webhook_endpoints (connection_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS integration_webhook_endpoints_external_idx
    ON vericov.integration_webhook_endpoints (tenant_id, provider_key, external_webhook_id)
    WHERE external_webhook_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS git_webhook_events_repository_received_idx
    ON vericov.git_webhook_events (repository_id, received_at DESC);

CREATE INDEX IF NOT EXISTS git_check_runs_repository_commit_idx
    ON vericov.git_check_runs (repository_id, commit_sha, name);

CREATE INDEX IF NOT EXISTS git_pr_comments_repository_pr_idx
    ON vericov.git_pr_comments (repository_id, pull_request_number, comment_key);

CREATE INDEX IF NOT EXISTS git_pr_annotations_repository_pr_idx
    ON vericov.git_pr_annotations (repository_id, pull_request_number);

CREATE INDEX IF NOT EXISTS git_branches_repository_branch_idx
    ON vericov.git_branches (repository_id, branch_name);

CREATE INDEX IF NOT EXISTS integration_sync_states_scope_status_idx
    ON vericov.integration_sync_states (tenant_id, scope_type, scope_id, status, sync_type);

CREATE INDEX IF NOT EXISTS integration_sync_states_connection_status_idx
    ON vericov.integration_sync_states (connection_id, status, sync_type);

CREATE INDEX IF NOT EXISTS integration_sync_states_next_run_idx
    ON vericov.integration_sync_states (status, next_run_at)
    WHERE next_run_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS integration_events_tenant_provider_status_received_idx
    ON vericov.integration_events (tenant_id, provider_key, status, received_at DESC);

CREATE INDEX IF NOT EXISTS integration_events_scope_status_idx
    ON vericov.integration_events (tenant_id, scope_type, scope_id, status, received_at DESC)
    WHERE scope_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS integration_events_connection_received_idx
    ON vericov.integration_events (connection_id, received_at DESC)
    WHERE connection_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS integration_events_external_event_idx
    ON vericov.integration_events (tenant_id, provider_key, external_event_id)
    WHERE external_event_id IS NOT NULL;

INSERT INTO vericov.integration_providers (
    provider_key,
    integration_type,
    display_name,
    auth_strategy,
    capabilities,
    default_config,
    status
)
VALUES
    (
        'github',
        'git',
        'GitHub',
        'github_app',
        ARRAY['git.webhooks', 'git.checks', 'git.comments', 'git.pull_requests', 'git.repository_sync'],
        '{"webhook_events":["pull_request","check_suite","issue_comment"]}'::jsonb,
        'active'
    ),
    (
        'gitlab',
        'git',
        'GitLab',
        'oauth_app',
        ARRAY['git.webhooks', 'git.checks', 'git.comments', 'git.pull_requests', 'git.repository_sync'],
        '{"webhook_events":["merge_request","pipeline","note"]}'::jsonb,
        'active'
    ),
    (
        'bitbucket',
        'git',
        'Bitbucket',
        'oauth_app',
        ARRAY['git.webhooks', 'git.checks', 'git.comments', 'git.pull_requests', 'git.repository_sync'],
        '{"webhook_events":["pullrequest:created","pullrequest:updated","repo:push"]}'::jsonb,
        'active'
    ),
    (
        'slack',
        'chat',
        'Slack',
        'oauth_app',
        ARRAY['chat.notifications'],
        '{}'::jsonb,
        'active'
    ),
    (
        'jira',
        'issue_tracker',
        'Jira',
        'oauth_app',
        ARRAY['issues.read', 'issues.comments'],
        '{}'::jsonb,
        'active'
    ),
    (
        'linear',
        'issue_tracker',
        'Linear',
        'oauth_app',
        ARRAY['issues.read', 'issues.comments'],
        '{}'::jsonb,
        'active'
    ),
    (
        'openai',
        'ai',
        'OpenAI',
        'api_key',
        ARRAY['ai.completions'],
        '{}'::jsonb,
        'active'
    )
ON CONFLICT (provider_key) DO UPDATE
SET
    integration_type = EXCLUDED.integration_type,
    display_name = EXCLUDED.display_name,
    auth_strategy = EXCLUDED.auth_strategy,
    capabilities = EXCLUDED.capabilities,
    default_config = EXCLUDED.default_config,
    status = EXCLUDED.status,
    updated_at = now();

ALTER TABLE vericov.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.organization_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repositories ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.organization_policy_defaults ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_gate_configurations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_badge_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_api_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_ci_trusts ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.runner_upload_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.uploads ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.upload_artifacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.analysis_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.analysis_job_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.coverage_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.badge_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.coverage_file_summaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.coverage_line_hits ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.test_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.pull_request_coverage_diffs ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.pull_request_coverage_diff_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.pull_request_coverage_diff_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.gate_evaluations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.upload_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_providers ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_webhook_endpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_sync_states ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_pull_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_check_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_pr_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_pr_annotations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_branches ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.coverage_debt_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.coverage_debt_events ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON ALL TABLES IN SCHEMA vericov FROM anon;
REVOKE ALL ON ALL TABLES IN SCHEMA vericov FROM authenticated;

CREATE OR REPLACE FUNCTION vericov.enqueue_coverage_analysis_job(p_analysis_job_id uuid)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = vericov, public
AS $$
DECLARE
    job record;
    message_id bigint;
BEGIN
    SELECT
        analysis_jobs.id AS analysis_job_id,
        analysis_jobs.upload_id,
        analysis_jobs.tenant_id,
        analysis_jobs.repository_id,
        analysis_jobs.commit_sha,
        analysis_jobs.queue_name
    INTO job
    FROM vericov.analysis_jobs
    WHERE analysis_jobs.id = p_analysis_job_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'analysis job % not found', p_analysis_job_id;
    END IF;

    SELECT send
    INTO message_id
    FROM pgmq.send(
        job.queue_name,
        jsonb_build_object(
            'schema_version', 1,
            'event_type', 'upload.received',
            'upload_id', job.upload_id,
            'analysis_job_id', job.analysis_job_id,
            'tenant_id', job.tenant_id,
            'repository_id', job.repository_id,
            'commit_sha', job.commit_sha
        ),
        0
    );

    UPDATE vericov.analysis_jobs
    SET queue_message_id = message_id,
        queued_at = now(),
        run_after = now()
    WHERE id = job.analysis_job_id;

    INSERT INTO vericov.upload_events (
        tenant_id,
        upload_id,
        event_type,
        payload
    )
    VALUES (
        job.tenant_id,
        job.upload_id,
        'analysis_job.created',
        jsonb_build_object(
            'analysis_job_id', job.analysis_job_id,
            'queue_name', job.queue_name,
            'queue_message_id', message_id
        )
    );

    RETURN message_id;
END;
$$;

REVOKE ALL ON FUNCTION vericov.enqueue_coverage_analysis_job(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION vericov.enqueue_coverage_analysis_job(uuid) FROM anon;
REVOKE ALL ON FUNCTION vericov.enqueue_coverage_analysis_job(uuid) FROM authenticated;

INSERT INTO storage.buckets (id, name)
VALUES
    (
        'coverage-raw',
        'coverage-raw'
    ),
    (
        'coverage-normalized',
        'coverage-normalized'
    ),
    (
        'test-results-raw',
        'test-results-raw'
    ),
    (
        'metadata-raw',
        'metadata-raw'
    )
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'storage'
          AND table_name = 'buckets'
          AND column_name = 'public'
    ) THEN
        EXECUTE $sql$
            UPDATE storage.buckets
            SET "public" = false
            WHERE id IN ('coverage-raw', 'coverage-normalized', 'test-results-raw', 'metadata-raw')
        $sql$;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'storage'
          AND table_name = 'buckets'
          AND column_name = 'file_size_limit'
    ) THEN
        EXECUTE $sql$
            UPDATE storage.buckets
            SET file_size_limit = CASE id
                WHEN 'coverage-raw' THEN 104857600
                WHEN 'coverage-normalized' THEN 104857600
                WHEN 'test-results-raw' THEN 104857600
                WHEN 'metadata-raw' THEN 10485760
                ELSE file_size_limit
            END
            WHERE id IN ('coverage-raw', 'coverage-normalized', 'test-results-raw', 'metadata-raw')
        $sql$;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'storage'
          AND table_name = 'buckets'
          AND column_name = 'allowed_mime_types'
    ) THEN
        EXECUTE $sql$
            UPDATE storage.buckets
            SET allowed_mime_types = CASE id
                WHEN 'coverage-raw' THEN ARRAY[
                    'application/json',
                    'application/gzip',
                    'application/octet-stream',
                    'application/xml',
                    'text/plain',
                    'text/xml'
                ]
                WHEN 'coverage-normalized' THEN ARRAY[
                    'application/gzip',
                    'application/json',
                    'application/octet-stream'
                ]
                WHEN 'test-results-raw' THEN ARRAY[
                    'application/json',
                    'application/gzip',
                    'application/octet-stream',
                    'application/xml',
                    'text/plain',
                    'text/xml'
                ]
                WHEN 'metadata-raw' THEN ARRAY[
                    'application/json',
                    'application/octet-stream',
                    'text/plain'
                ]
                ELSE allowed_mime_types
            END
            WHERE id IN ('coverage-raw', 'coverage-normalized', 'test-results-raw', 'metadata-raw')
        $sql$;
    END IF;
END;
$$;
