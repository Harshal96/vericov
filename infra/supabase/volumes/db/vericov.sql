CREATE SCHEMA IF NOT EXISTS vericov;
CREATE SCHEMA IF NOT EXISTS extensions;

REVOKE ALL ON SCHEMA vericov FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA vericov FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA vericov FROM authenticated';
    END IF;
END
$$;

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
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'),
    external_org_id uuid UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE vericov.tenants
    ADD COLUMN IF NOT EXISTS external_org_id uuid UNIQUE;

CREATE TABLE IF NOT EXISTS vericov.repositories (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    provider text NOT NULL CHECK (provider IN ('github', 'gitlab', 'bitbucket', 'local')),
    provider_repository_id text NOT NULL CHECK (length(trim(provider_repository_id)) BETWEEN 1 AND 120),
    full_name text NOT NULL CHECK (length(trim(full_name)) BETWEEN 1 AND 300),
    default_branch text NOT NULL DEFAULT 'main'
        CHECK (length(trim(default_branch)) BETWEEN 1 AND 255),
    visibility text NOT NULL DEFAULT 'private'
        CHECK (visibility IN ('public', 'private', 'internal')),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled', 'archived')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider, provider_repository_id)
);

INSERT INTO vericov.tenants (id, name, slug)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Local workspace',
    'local'
)
ON CONFLICT DO NOTHING;

UPDATE vericov.tenants
SET external_org_id = '00000000-0000-0000-0000-000000000010'
WHERE id = '00000000-0000-0000-0000-000000000001'
  AND external_org_id IS NULL;

INSERT INTO vericov.repositories (
    id,
    tenant_id,
    provider,
    provider_repository_id,
    full_name,
    default_branch,
    visibility
)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'local',
    'local',
    'local/repository',
    'main',
    'private'
)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS vericov.repository_gate_configurations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    gate_type text NOT NULL CHECK (
        gate_type IN ('project_coverage', 'patch_coverage', 'coverage_drop', 'component_coverage')
    ),
    metric text NOT NULL CHECK (metric IN ('line', 'branch', 'function', 'statement', 'risk')),
    threshold numeric(12, 4) CHECK (threshold IS NULL OR threshold >= 0),
    max_drop numeric(12, 4) CHECK (max_drop IS NULL OR max_drop >= 0),
    blocking boolean NOT NULL DEFAULT true,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(config_json) = 'object'),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (repository_id, name),
    CHECK (threshold IS NOT NULL),
    CHECK (gate_type = 'coverage_drop' OR max_drop IS NULL)
);

CREATE TABLE IF NOT EXISTS vericov.repository_api_keys (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    key_prefix text NOT NULL CHECK (length(trim(key_prefix)) BETWEEN 1 AND 64),
    key_hash text NOT NULL CHECK (length(key_hash) = 64),
    scopes text[] NOT NULL DEFAULT ARRAY['uploads:create', 'uploads:read'],
    branch_allow_patterns text[] NOT NULL DEFAULT ARRAY['*'],
    expires_at timestamptz,
    revoked_at timestamptz,
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
    provider text NOT NULL CHECK (provider = 'github_actions'),
    issuer text NOT NULL CHECK (issuer = 'https://token.actions.githubusercontent.com'),
    audience text NOT NULL CHECK (length(trim(audience)) BETWEEN 1 AND 255),
    subject_pattern text NOT NULL CHECK (length(trim(subject_pattern)) BETWEEN 1 AND 500),
    scopes text[] NOT NULL DEFAULT ARRAY['uploads:create', 'uploads:read'],
    branch_allow_patterns text[] NOT NULL DEFAULT ARRAY['*'],
    expires_at timestamptz,
    revoked_at timestamptz,
    last_used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (repository_id, provider, subject_pattern),
    CHECK (cardinality(scopes) > 0),
    CHECK (cardinality(branch_allow_patterns) > 0)
);

CREATE TABLE IF NOT EXISTS vericov.uploads (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    api_key_id uuid REFERENCES vericov.repository_api_keys (id) ON DELETE SET NULL,
    commit_sha text NOT NULL CHECK (length(trim(commit_sha)) BETWEEN 1 AND 128),
    branch text NOT NULL CHECK (length(trim(branch)) BETWEEN 1 AND 255),
    pull_request_number integer CHECK (pull_request_number IS NULL OR pull_request_number > 0),
    base_sha text CHECK (base_sha IS NULL OR length(trim(base_sha)) BETWEEN 1 AND 128),
    ci_provider text,
    ci_build_id text,
    ci_build_url text,
    flags text[] NOT NULL DEFAULT ARRAY[]::text[],
    ignore_rules jsonb NOT NULL DEFAULT '[]'::jsonb,
    config_snapshot_json jsonb,
    config_sha256 text,
    component text,
    package_name text,
    status text NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'processing', 'processed', 'failed', 'rejected')),
    idempotency_key text NOT NULL CHECK (length(trim(idempotency_key)) BETWEEN 1 AND 255),
    accepted_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    error_code text,
    error_message text,
    UNIQUE (repository_id, idempotency_key)
);

ALTER TABLE vericov.uploads
    ADD COLUMN IF NOT EXISTS ignore_rules jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE vericov.uploads
    ADD COLUMN IF NOT EXISTS config_snapshot_json jsonb,
    ADD COLUMN IF NOT EXISTS config_sha256 text;

ALTER TABLE vericov.uploads
    ADD COLUMN IF NOT EXISTS base_sha text;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uploads_ignore_rules_array'
          AND conrelid = 'vericov.uploads'::regclass
    ) THEN
        ALTER TABLE vericov.uploads
            ADD CONSTRAINT uploads_ignore_rules_array
            CHECK (jsonb_typeof(ignore_rules) = 'array');
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uploads_base_sha_format'
          AND conrelid = 'vericov.uploads'::regclass
    ) THEN
        ALTER TABLE vericov.uploads
            ADD CONSTRAINT uploads_base_sha_format
            CHECK (base_sha IS NULL OR length(trim(base_sha)) BETWEEN 1 AND 128);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uploads_config_snapshot_object'
          AND conrelid = 'vericov.uploads'::regclass
    ) THEN
        ALTER TABLE vericov.uploads
            ADD CONSTRAINT uploads_config_snapshot_object
            CHECK (config_snapshot_json IS NULL OR jsonb_typeof(config_snapshot_json) = 'object');
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uploads_config_sha256_format'
          AND conrelid = 'vericov.uploads'::regclass
    ) THEN
        ALTER TABLE vericov.uploads
            ADD CONSTRAINT uploads_config_sha256_format
            CHECK (config_sha256 IS NULL OR config_sha256 ~ '^[0-9a-f]{64}$');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS vericov.upload_artifacts (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    upload_id uuid NOT NULL REFERENCES vericov.uploads (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 512),
    kind text NOT NULL CHECK (kind IN ('coverage', 'test_results', 'metadata', 'diff')),
    format text NOT NULL CHECK (length(trim(format)) BETWEEN 1 AND 64),
    content_type text NOT NULL CHECK (length(trim(content_type)) BETWEEN 1 AND 255),
    size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
    storage_bucket text NOT NULL,
    storage_path text NOT NULL,
    sha256_hex text NOT NULL CHECK (length(sha256_hex) = 64),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (upload_id, name)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'upload_artifacts_kind_check'
          AND conrelid = 'vericov.upload_artifacts'::regclass
          AND pg_get_constraintdef(oid) NOT LIKE '%diff%'
    ) THEN
        ALTER TABLE vericov.upload_artifacts
            DROP CONSTRAINT upload_artifacts_kind_check;
        ALTER TABLE vericov.upload_artifacts
            ADD CONSTRAINT upload_artifacts_kind_check
            CHECK (kind IN ('coverage', 'test_results', 'metadata', 'diff'));
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS upload_artifacts_single_diff_idx
    ON vericov.upload_artifacts (upload_id)
    WHERE kind = 'diff';

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
    pull_request_number integer CHECK (pull_request_number IS NULL OR pull_request_number > 0),
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
    config_sha256 text
        CONSTRAINT coverage_reports_config_sha256_format
        CHECK (config_sha256 IS NULL OR config_sha256 ~ '^[0-9a-f]{64}$'),
    gate_status text NOT NULL DEFAULT 'not_evaluated'
        CONSTRAINT coverage_reports_gate_status_valid
        CHECK (gate_status IN ('passed', 'failed', 'warning', 'not_evaluated')),
    warnings_json jsonb NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT coverage_reports_warnings_array
        CHECK (jsonb_typeof(warnings_json) = 'array'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (line_covered <= line_total),
    CHECK (branch_covered <= branch_total),
    CHECK (function_covered <= function_total),
    CHECK (statement_covered <= statement_total)
);

ALTER TABLE vericov.coverage_reports
    ADD COLUMN IF NOT EXISTS config_sha256 text,
    ADD COLUMN IF NOT EXISTS gate_status text NOT NULL DEFAULT 'not_evaluated',
    ADD COLUMN IF NOT EXISTS warnings_json jsonb NOT NULL DEFAULT '[]'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'coverage_reports_config_sha256_format'
          AND conrelid = 'vericov.coverage_reports'::regclass
    ) THEN
        ALTER TABLE vericov.coverage_reports
            ADD CONSTRAINT coverage_reports_config_sha256_format
            CHECK (config_sha256 IS NULL OR config_sha256 ~ '^[0-9a-f]{64}$');
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'coverage_reports_gate_status_valid'
          AND conrelid = 'vericov.coverage_reports'::regclass
    ) THEN
        ALTER TABLE vericov.coverage_reports
            ADD CONSTRAINT coverage_reports_gate_status_valid
            CHECK (gate_status IN ('passed', 'failed', 'warning', 'not_evaluated'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'coverage_reports_warnings_array'
          AND conrelid = 'vericov.coverage_reports'::regclass
    ) THEN
        ALTER TABLE vericov.coverage_reports
            ADD CONSTRAINT coverage_reports_warnings_array
            CHECK (jsonb_typeof(warnings_json) = 'array');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS vericov.coverage_file_summaries (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    commit_sha text NOT NULL,
    file_path text NOT NULL,
    leaf_component_key text,
    package_name text,
    owners text[] NOT NULL DEFAULT ARRAY[]::text[],
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

ALTER TABLE vericov.coverage_file_summaries
    ADD COLUMN IF NOT EXISTS leaf_component_key text;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'vericov'
          AND table_name = 'coverage_file_summaries'
          AND column_name = 'component_id'
    ) THEN
        UPDATE vericov.coverage_file_summaries
        SET leaf_component_key = component_id::text
        WHERE leaf_component_key IS NULL AND component_id IS NOT NULL;
        ALTER TABLE vericov.coverage_file_summaries DROP COLUMN component_id;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS vericov.component_coverage_rollups (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    component_key text NOT NULL,
    parent_component_key text,
    component_path text[] NOT NULL,
    depth integer NOT NULL CHECK (depth >= 0),
    position integer NOT NULL CHECK (position >= 0),
    name text NOT NULL,
    owners text[] NOT NULL DEFAULT ARRAY[]::text[],
    effective_gates_json jsonb NOT NULL DEFAULT '{}'::jsonb
        CONSTRAINT component_coverage_rollups_effective_gates_object
        CHECK (jsonb_typeof(effective_gates_json) = 'object'),
    line_covered integer NOT NULL CHECK (line_covered >= 0),
    line_total integer NOT NULL CHECK (line_total >= 0),
    branch_covered integer NOT NULL CHECK (branch_covered >= 0),
    branch_total integer NOT NULL CHECK (branch_total >= 0),
    function_covered integer NOT NULL CHECK (function_covered >= 0),
    function_total integer NOT NULL CHECK (function_total >= 0),
    statement_covered integer NOT NULL CHECK (statement_covered >= 0),
    statement_total integer NOT NULL CHECK (statement_total >= 0),
    direct_file_count integer NOT NULL DEFAULT 0 CHECK (direct_file_count >= 0),
    descendant_file_count integer NOT NULL DEFAULT 0 CHECK (descendant_file_count >= 0),
    gap_count integer NOT NULL DEFAULT 0 CHECK (gap_count >= 0),
    debt_count integer NOT NULL DEFAULT 0 CHECK (debt_count >= 0),
    risk_score_total numeric(12, 4) NOT NULL DEFAULT 0 CHECK (risk_score_total >= 0),
    highest_active_risk_level text
        CHECK (highest_active_risk_level IS NULL OR highest_active_risk_level IN ('critical', 'high', 'medium', 'low')),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT component_coverage_rollups_report_component_key
        UNIQUE (coverage_report_id, component_key),
    CHECK (line_covered <= line_total),
    CHECK (branch_covered <= branch_total),
    CHECK (function_covered <= function_total),
    CHECK (statement_covered <= statement_total)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'vericov'
          AND table_name = 'component_coverage_rollups'
          AND column_name = 'component_id'
    ) THEN
        DELETE FROM vericov.component_coverage_rollups;
        ALTER TABLE vericov.component_coverage_rollups
            DROP CONSTRAINT IF EXISTS component_coverage_rollups_coverage_report_id_component_id_owner_key,
            DROP COLUMN component_id,
            DROP COLUMN owner;
    END IF;
END
$$;

ALTER TABLE vericov.component_coverage_rollups
    ADD COLUMN IF NOT EXISTS component_key text,
    ADD COLUMN IF NOT EXISTS parent_component_key text,
    ADD COLUMN IF NOT EXISTS component_path text[] NOT NULL DEFAULT ARRAY[]::text[],
    ADD COLUMN IF NOT EXISTS depth integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS position integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS name text NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS owners text[] NOT NULL DEFAULT ARRAY[]::text[],
    ADD COLUMN IF NOT EXISTS effective_gates_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS direct_file_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS descendant_file_count integer NOT NULL DEFAULT 0;

DELETE FROM vericov.component_coverage_rollups
WHERE component_key IS NULL;

ALTER TABLE vericov.component_coverage_rollups
    ALTER COLUMN component_key SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'component_coverage_rollups_report_component_key'
          AND conrelid = 'vericov.component_coverage_rollups'::regclass
    ) THEN
        ALTER TABLE vericov.component_coverage_rollups
            ADD CONSTRAINT component_coverage_rollups_report_component_key
            UNIQUE (coverage_report_id, component_key);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'component_coverage_rollups_effective_gates_object'
          AND conrelid = 'vericov.component_coverage_rollups'::regclass
    ) THEN
        ALTER TABLE vericov.component_coverage_rollups
            ADD CONSTRAINT component_coverage_rollups_effective_gates_object
            CHECK (jsonb_typeof(effective_gates_json) = 'object');
    END IF;
END
$$;

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
    pull_request_number integer CHECK (pull_request_number IS NULL OR pull_request_number > 0),
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
    base_hits bigint CHECK (base_hits IS NULL OR base_hits >= 0),
    head_hits bigint CHECK (head_hits IS NULL OR head_hits >= 0),
    newly_missed boolean NOT NULL DEFAULT false,
    lost_coverage boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.coverage_gap_findings (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    coverage_report_id uuid NOT NULL REFERENCES vericov.coverage_reports (id) ON DELETE CASCADE,
    pr_diff_id uuid REFERENCES vericov.pull_request_coverage_diffs (id) ON DELETE SET NULL,
    component_key text,
    commit_sha text NOT NULL,
    pull_request_number integer CHECK (pull_request_number IS NULL OR pull_request_number > 0),
    file_path text NOT NULL,
    target_type text NOT NULL CHECK (target_type IN ('line', 'range', 'file', 'function', 'branch', 'component')),
    line_start integer CHECK (line_start IS NULL OR line_start > 0),
    line_end integer CHECK (line_end IS NULL OR line_end > 0),
    symbol_name text,
    reason_code text NOT NULL CHECK (length(trim(reason_code)) BETWEEN 1 AND 120),
    explanation text NOT NULL CHECK (length(trim(explanation)) BETWEEN 1 AND 1000),
    confidence text NOT NULL CHECK (confidence IN ('high', 'medium', 'low')),
    risk_score numeric(5, 1) NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    risk_level text NOT NULL CHECK (risk_level IN ('critical', 'high', 'medium', 'low')),
    owners text[] NOT NULL DEFAULT ARRAY[]::text[],
    next_action text NOT NULL CHECK (
        next_action IN ('add_test', 'create_debt', 'mark_generated', 'inspect_instrumentation', 'run_source_explain')
    ),
    status text NOT NULL CHECK (status IN ('active', 'debt_suppressed', 'resolved', 'obsolete')),
    evidence_json jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(evidence_json) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (repository_id, coverage_report_id, file_path, target_type, line_start, line_end, reason_code),
    CHECK (line_end IS NULL OR line_start IS NULL OR line_end >= line_start)
);

ALTER TABLE vericov.coverage_gap_findings
    ADD COLUMN IF NOT EXISTS component_key text;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'vericov'
          AND table_name = 'coverage_gap_findings'
          AND column_name = 'component_id'
    ) THEN
        UPDATE vericov.coverage_gap_findings
        SET component_key = component_id::text
        WHERE component_key IS NULL AND component_id IS NOT NULL;
        ALTER TABLE vericov.coverage_gap_findings DROP COLUMN component_id;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS vericov.gate_evaluations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    coverage_report_id uuid REFERENCES vericov.coverage_reports (id) ON DELETE SET NULL,
    commit_sha text NOT NULL CHECK (length(trim(commit_sha)) BETWEEN 1 AND 128),
    branch text NOT NULL CHECK (length(trim(branch)) BETWEEN 1 AND 255),
    pull_request_number integer CHECK (pull_request_number IS NULL OR pull_request_number > 0),
    gate_name text NOT NULL CHECK (length(trim(gate_name)) BETWEEN 1 AND 120),
    gate_type text NOT NULL,
    metric text NOT NULL,
    threshold numeric(12, 4),
    actual numeric(12, 4),
    status text NOT NULL CHECK (status IN ('passed', 'failed', 'warning')),
    blocking boolean NOT NULL DEFAULT true,
    source text NOT NULL DEFAULT 'repository'
        CONSTRAINT gate_evaluations_source_valid
        CHECK (source IN ('repository', 'component_config')),
    scope_type text NOT NULL DEFAULT 'repository'
        CONSTRAINT gate_evaluations_scope_type_valid
        CHECK (scope_type IN ('repository', 'component')),
    scope_key text,
    scope_path text[] NOT NULL DEFAULT ARRAY[]::text[],
    details_json jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(details_json) = 'object'),
    evaluated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE vericov.gate_evaluations
    ADD COLUMN IF NOT EXISTS source text NOT NULL DEFAULT 'repository',
    ADD COLUMN IF NOT EXISTS scope_type text NOT NULL DEFAULT 'repository',
    ADD COLUMN IF NOT EXISTS scope_key text,
    ADD COLUMN IF NOT EXISTS scope_path text[] NOT NULL DEFAULT ARRAY[]::text[];

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'gate_evaluations_source_valid'
          AND conrelid = 'vericov.gate_evaluations'::regclass
    ) THEN
        ALTER TABLE vericov.gate_evaluations
            ADD CONSTRAINT gate_evaluations_source_valid
            CHECK (source IN ('repository', 'component_config'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'gate_evaluations_scope_type_valid'
          AND conrelid = 'vericov.gate_evaluations'::regclass
    ) THEN
        ALTER TABLE vericov.gate_evaluations
            ADD CONSTRAINT gate_evaluations_scope_type_valid
            CHECK (scope_type IN ('repository', 'component'));
    END IF;
END
$$;

UPDATE vericov.repository_gate_configurations
SET status = 'disabled',
    updated_at = now()
WHERE gate_type = 'component_coverage'
  AND status = 'active';

CREATE TABLE IF NOT EXISTS vericov.upload_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    upload_id uuid NOT NULL REFERENCES vericov.uploads (id) ON DELETE CASCADE,
    event_type text NOT NULL CHECK (length(trim(event_type)) BETWEEN 1 AND 120),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(payload) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS repositories_tenant_idx
    ON vericov.repositories (tenant_id);
CREATE INDEX IF NOT EXISTS repository_gate_configurations_repository_status_idx
    ON vericov.repository_gate_configurations (repository_id, status);
CREATE INDEX IF NOT EXISTS repository_api_keys_repository_active_idx
    ON vericov.repository_api_keys (repository_id)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS repository_api_keys_prefix_active_idx
    ON vericov.repository_api_keys (key_prefix)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS repository_ci_trusts_repository_active_idx
    ON vericov.repository_ci_trusts (repository_id, provider)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS uploads_repository_commit_idx
    ON vericov.uploads (repository_id, commit_sha);
CREATE INDEX IF NOT EXISTS uploads_status_accepted_idx
    ON vericov.uploads (status, accepted_at);
CREATE INDEX IF NOT EXISTS upload_artifacts_upload_idx
    ON vericov.upload_artifacts (upload_id);
CREATE INDEX IF NOT EXISTS analysis_jobs_status_run_after_priority_idx
    ON vericov.analysis_jobs (status, run_after, priority, queued_at);
CREATE INDEX IF NOT EXISTS analysis_job_attempts_job_idx
    ON vericov.analysis_job_attempts (analysis_job_id, attempt_number);
CREATE INDEX IF NOT EXISTS coverage_reports_repository_commit_idx
    ON vericov.coverage_reports (repository_id, commit_sha);
CREATE INDEX IF NOT EXISTS coverage_reports_repository_branch_created_idx
    ON vericov.coverage_reports (repository_id, branch, created_at DESC);
CREATE INDEX IF NOT EXISTS coverage_file_summaries_repository_file_idx
    ON vericov.coverage_file_summaries (repository_id, file_path);
CREATE INDEX IF NOT EXISTS component_coverage_rollups_report_idx
    ON vericov.component_coverage_rollups (coverage_report_id);
CREATE INDEX IF NOT EXISTS coverage_gap_findings_rank_idx
    ON vericov.coverage_gap_findings (repository_id, status, risk_score DESC, file_path, line_start);
CREATE INDEX IF NOT EXISTS coverage_gap_findings_report_status_risk_idx
    ON vericov.coverage_gap_findings (coverage_report_id, status, risk_score DESC);
CREATE INDEX IF NOT EXISTS coverage_gap_findings_manifest_idx
    ON vericov.coverage_gap_findings (coverage_report_id, next_action, status, risk_score DESC);
CREATE INDEX IF NOT EXISTS coverage_gap_findings_owners_idx
    ON vericov.coverage_gap_findings USING gin (owners);
CREATE INDEX IF NOT EXISTS coverage_line_hits_report_file_idx
    ON vericov.coverage_line_hits (coverage_report_id, file_path, line_number);
CREATE INDEX IF NOT EXISTS test_runs_upload_idx
    ON vericov.test_runs (upload_id, suite_index);
CREATE INDEX IF NOT EXISTS pr_coverage_diffs_repository_pr_idx
    ON vericov.pull_request_coverage_diffs (repository_id, pull_request_number, created_at DESC);
CREATE INDEX IF NOT EXISTS pr_coverage_diff_files_diff_idx
    ON vericov.pull_request_coverage_diff_files (pr_diff_id, file_path);
CREATE INDEX IF NOT EXISTS pr_coverage_diff_lines_diff_file_idx
    ON vericov.pull_request_coverage_diff_lines (pr_diff_id, file_path, head_line_number);
CREATE INDEX IF NOT EXISTS gate_evaluations_report_idx
    ON vericov.gate_evaluations (coverage_report_id);
CREATE INDEX IF NOT EXISTS upload_events_upload_created_idx
    ON vericov.upload_events (upload_id, created_at);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA vericov FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA vericov FROM authenticated';
    END IF;
END
$$;

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
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION vericov.enqueue_coverage_analysis_job(uuid) FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON FUNCTION vericov.enqueue_coverage_analysis_job(uuid) FROM authenticated';
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('storage.buckets') IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO storage.buckets (id, name)
    VALUES
        ('coverage-raw', 'coverage-raw'),
        ('coverage-normalized', 'coverage-normalized'),
        ('test-results-raw', 'test-results-raw'),
        ('metadata-raw', 'metadata-raw')
    ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name;

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
                WHEN 'metadata-raw' THEN 10485760
                ELSE 104857600
            END
            WHERE id IN ('coverage-raw', 'coverage-normalized', 'test-results-raw', 'metadata-raw')
        $sql$;
    END IF;
END;
$$;
