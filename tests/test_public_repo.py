from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_root_pytest_is_scoped_to_root_tests() -> None:
    pytest_config = read("pytest.ini")

    assert "testpaths = tests" in pytest_config


def test_bundled_database_uses_the_pinned_schema_compatible_image() -> None:
    compose = read("infra/local/docker-compose.yml")

    assert "image: supabase/postgres:15.8.1.085" in compose
    assert (
        "../../infra/supabase/volumes/db/vericov.sql:"
        "/docker-entrypoint-initdb.d/init-scripts/900-vericov.sql:ro"
    ) in compose


def test_bundled_database_mounts_required_bootstrap_scripts() -> None:
    compose = read("infra/local/docker-compose.yml")

    assert (
        "../../infra/supabase/volumes/db/roles.sql:"
        "/docker-entrypoint-initdb.d/init-scripts/910-roles.sql:ro"
    ) in compose
    assert (
        "../../infra/supabase/volumes/db/jwt.sql:"
        "/docker-entrypoint-initdb.d/init-scripts/920-jwt.sql:ro"
    ) in compose


def test_migrate_command_applies_the_tracked_database_schema() -> None:
    launcher = read("vericov")

    assert 'SCHEMA_FILE="$ROOT_DIR/infra/supabase/volumes/db/vericov.sql"' in launcher
    assert 'psql "$db_url"' in launcher
    assert '-f "$SCHEMA_FILE"' in launcher


def test_init_command_generates_credentials_instead_of_copying_placeholders() -> None:
    launcher = read("vericov")
    generator = read("infra/local/scripts/init-env.mjs")
    readme = read("README.md")

    assert "init        Generate .env with random local credentials" in launcher
    assert "randomBytes" in generator
    assert "mode: 0o600" in generator
    assert "./vericov init" in readme
    assert "cp .env.example .env" not in readme


def test_public_configuration_only_documents_supported_auth_modes() -> None:
    env_example = read(".env.example")
    launcher = read("vericov")
    self_hosting = read("docs/SELF_HOSTING.md")

    assert "VERICOV_SERVICE_JWT" not in env_example
    assert "VERICOV_SERVICE_JWT" not in launcher
    assert "service-JWT" not in self_hosting
    assert "VERICOV_REPO_API_KEY_PEPPER" in launcher


def test_public_launcher_only_supports_bring_your_own_supabase_storage() -> None:
    env_example = read(".env.example")
    launcher = read("vericov")
    self_hosting = read("docs/SELF_HOSTING.md")

    assert "BYO_SUPABASE=0" not in env_example
    assert "ensure_supabase_env" not in launcher
    assert "BYO_SUPABASE must be 1 or skip" in launcher
    assert "optional bundled Supabase" not in self_hosting


def test_database_schema_supports_postgres_without_supabase_storage() -> None:
    schema = read("infra/supabase/volumes/db/vericov.sql")

    assert "CREATE SCHEMA IF NOT EXISTS extensions;" in schema
    assert "IF to_regclass('storage.buckets') IS NULL THEN" in schema
    assert "WHERE rolname = 'anon'" in schema
    assert "WHERE rolname = 'authenticated'" in schema


def test_local_schema_seeds_the_default_upload_workspace() -> None:
    schema = read("infra/supabase/volumes/db/vericov.sql")
    env_example = read(".env.example")

    assert "00000000-0000-0000-0000-000000000001" in schema
    assert "00000000-0000-0000-0000-000000000003" in schema
    assert "VERICOV_DEV_REPOSITORY_ID=00000000-0000-0000-0000-000000000003" in env_example


def test_public_schema_contains_only_single_workspace_product_tables() -> None:
    schema = read("infra/supabase/volumes/db/vericov.sql")

    assert "vericov.organizations" not in schema
    assert "vericov.memberships" not in schema
    assert "vericov.organization_invitations" not in schema
    assert "org_id" not in schema
    assert "vericov.integration_" not in schema
    assert "vericov.agent_" not in schema


def test_analysis_runtime_has_no_removed_service_clients() -> None:
    components = read("services/coverage-analysis/src/main/java/dev/vericov/analysis/config/AnalysisComponents.java")
    gate_evaluator = read("services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateEvaluator.java")

    assert "InternalControlPlaneRepositoryContextClient" not in components
    assert "InternalGitDiffHttpClient" not in components
    assert "VERICOV_INTERNAL_SERVICE_TOKEN" not in components
    assert "VERICOV_CONTROL_PLANE_BASE_URL" not in components
    assert "VERICOV_GIT_BASE_URL" not in components
    assert "agent_review_required" not in gate_evaluator


def test_local_services_share_filesystem_artifact_storage_by_default() -> None:
    compose = read("infra/local/docker-compose.yml")
    env_example = read(".env.example")

    assert "VERICOV_COMPOSE_ENV_FILE:?run through ./vericov" in compose
    assert "VERICOV_ARTIFACT_STORAGE_BACKEND: ${VERICOV_ARTIFACT_STORAGE_BACKEND:-filesystem}" in compose
    assert compose.count("vericov-artifacts:/var/lib/vericov/artifacts") == 2
    assert "vericov-artifacts:" in compose
    assert "VERICOV_ARTIFACT_STORAGE_BACKEND=filesystem" in env_example


def test_public_quick_start_only_starts_the_two_core_services() -> None:
    compose = read("infra/local/docker-compose.yml")
    self_hosting = read("docs/SELF_HOSTING.md")

    assert "control-plane:" not in compose
    assert "git-integration:" not in compose
    assert "integrations:" not in compose
    assert "agent-runner:" not in compose
    assert "two Helidon services" in self_hosting


def test_legacy_multi_service_launchers_are_removed() -> None:
    for path in (
        "scripts/dev-up.sh",
        "scripts/dev-down.sh",
        "infra/local/.env.example",
        "infra/local/scripts/generate-env.mjs",
    ):
        assert not (ROOT / path).exists()


def test_public_repository_has_contributor_and_security_documents() -> None:
    for path in ("CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md", "docs/OPERATIONS.md"):
        assert (ROOT / path).is_file(), f"{path} is required"


def test_ci_runs_java_python_and_configuration_checks() -> None:
    workflow = read(".github/workflows/ci.yml")

    assert "mvn --batch-mode verify" in workflow
    assert "pytest -q" in workflow
    assert "--cov-fail-under=80" in workflow
    assert "./vericov doctor" in workflow
    assert "docker compose" in workflow
    assert "./vericov init" in workflow
    assert "./scripts/smoke-test.sh" in workflow


def test_java_modules_enforce_eighty_percent_line_coverage() -> None:
    for path in (
        "libraries/coverage-ignore/pom.xml",
        "services/upload/pom.xml",
        "services/coverage-analysis/pom.xml",
    ):
        pom = read(path)

        assert "<artifactId>jacoco-maven-plugin</artifactId>" in pom
        assert "<counter>LINE</counter>" in pom
        assert "<minimum>0.80</minimum>" in pom


def test_service_container_build_includes_shared_java_libraries() -> None:
    dockerfile = read("infra/docker/helidon-service.Dockerfile")

    assert "COPY libraries libraries" in dockerfile


def test_upload_cli_declares_coverage_test_tooling() -> None:
    pyproject = read("clis/coverage-upload/pyproject.toml")

    assert '"pytest-cov>=6"' in pyproject


def test_smoke_test_exercises_upload_to_analysis_flow() -> None:
    smoke_test = read("scripts/smoke-test.sh")

    assert "/api/v1/uploads" in smoke_test
    assert "/report" in smoke_test
    assert '"content_base64"' in smoke_test
    assert '"ignore": ["generated/**"]' in smoke_test
    assert "generated/Generated.java" in smoke_test
    assert '"line":{"covered":1,"total":1}' in smoke_test
    assert "was analyzed" in smoke_test


def test_public_docs_define_the_coverage_file_exclusion_contract() -> None:
    root_readme = read("README.md")
    cli_readme = read("clis/coverage-upload/README.md")

    assert ".vericov.yml" in root_readme
    assert "The only supported configuration filename is `.vericov.yml`." in cli_readme
    assert "upload.discover.exclude" in cli_readme
    assert "!vendor/maintained/**" in cli_readme
    assert "future uploads only" in cli_readme
    assert "empty `0/0` coverage report" in cli_readme
    assert "`.vericov.yml` is also accepted" not in cli_readme


def test_public_docs_define_hierarchical_component_coverage() -> None:
    root_readme = read("README.md")
    cli_readme = read("clis/coverage-upload/README.md")
    self_hosting = read("docs/SELF_HOSTING.md")

    assert "components:" in root_readme
    assert "key: payments-api" in cli_readme
    assert "applies `ignore` rules before component matching" in cli_readme
    assert "most-specific matching leaf" in cli_readme
    assert "stable, globally unique lowercase identifiers" in cli_readme
    assert "Parent coverage and gates include all" in cli_readme
    assert "descendant files" in cli_readme
    assert "`gate_status: failed`" in cli_readme
    assert "future uploads only" in cli_readme
    assert "config_sha256" in self_hosting


def test_upload_schema_persists_immutable_configuration_snapshot() -> None:
    schema = read("infra/supabase/volumes/db/vericov.sql")

    assert "ignore_rules jsonb NOT NULL DEFAULT '[]'::jsonb" in schema
    assert "uploads_ignore_rules_array" in schema
    assert "config_snapshot_json jsonb" in schema
    assert "config_sha256 text" in schema
    assert "component_key text NOT NULL" in schema
    assert "leaf_component_key text" in schema


def test_release_builds_the_real_upload_cli_package() -> None:
    workflow = read(".github/workflows/release.yml")

    assert "clis/coverage-upload" in workflow
    assert "dist/" in workflow
    assert 'coverage-upload-v*' in workflow
    assert "coverage-upload-v${PACKAGE_VERSION}" in workflow
    assert "types: [published]" not in workflow


def test_root_is_not_published_as_a_placeholder_python_package() -> None:
    assert not (ROOT / "pyproject.toml").exists()
    assert not (ROOT / "src/vericov").exists()
