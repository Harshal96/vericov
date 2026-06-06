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


def test_public_schema_has_no_membership_or_invitation_product_tables() -> None:
    schema = read("infra/supabase/volumes/db/vericov.sql")

    assert "vericov.memberships" not in schema
    assert "vericov.organization_invitations" not in schema


def test_local_services_share_filesystem_artifact_storage_by_default() -> None:
    compose = read("infra/local/docker-compose.yml")
    env_example = read(".env.example")

    assert "VERICOV_ARTIFACT_STORAGE_BACKEND: ${VERICOV_ARTIFACT_STORAGE_BACKEND:-filesystem}" in compose
    assert compose.count("vericov-artifacts:/var/lib/vericov/artifacts") == 2
    assert "vericov-artifacts:" in compose
    assert "VERICOV_ARTIFACT_STORAGE_BACKEND=filesystem" in env_example


def test_public_quick_start_only_starts_the_three_core_services() -> None:
    compose = read("infra/local/docker-compose.yml")
    self_hosting = read("docs/SELF_HOSTING.md")

    assert "profiles:\n      - integrations" in compose
    assert "profiles:\n      - agents" in compose
    assert "three core Helidon services" in self_hosting


def test_public_repository_has_contributor_and_security_documents() -> None:
    for path in ("CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md", "docs/OPERATIONS.md"):
        assert (ROOT / path).is_file(), f"{path} is required"


def test_ci_runs_java_python_and_configuration_checks() -> None:
    workflow = read(".github/workflows/ci.yml")

    assert "mvn --batch-mode test" in workflow
    assert "pytest -q" in workflow
    assert "./vericov doctor" in workflow
    assert "docker compose" in workflow
    assert "./vericov init" in workflow
    assert "./scripts/smoke-test.sh" in workflow


def test_smoke_test_exercises_upload_to_analysis_flow() -> None:
    smoke_test = read("scripts/smoke-test.sh")

    assert "/api/v1/uploads" in smoke_test
    assert '"content_base64"' in smoke_test
    assert "was analyzed" in smoke_test


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
