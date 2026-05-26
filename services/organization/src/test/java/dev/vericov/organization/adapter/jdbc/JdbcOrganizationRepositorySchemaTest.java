package dev.vericov.organization.adapter.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcOrganizationRepositorySchemaTest {
    @Test
    void schemaDefinesBadgeCacheTable() throws Exception {
        String sql = Files.readString(Path.of("../../infra/supabase/volumes/db/vericov.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.badge_cache"));
        assertTrue(sql.contains("UNIQUE (org_id, repository_id, badge_type, cache_scope, branch, metric)"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS badge_cache_expires_at_idx"));
        assertTrue(sql.contains("ALTER TABLE vericov.badge_cache ENABLE ROW LEVEL SECURITY"));
    }

    @Test
    void schemaDefinesTestRunsTable() throws Exception {
        String sql = Files.readString(Path.of("../../infra/supabase/volumes/db/vericov.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.test_runs"));
        assertTrue(sql.contains("CHECK (status IN ('passed', 'failed', 'error', 'skipped'))"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS test_runs_repository_commit_idx"));
        assertTrue(sql.contains("ALTER TABLE vericov.test_runs ENABLE ROW LEVEL SECURITY"));
    }

    @Test
    void schemaDefinesRepositoryComponentsAndCoverageContextTables() throws Exception {
        String sql = Files.readString(Path.of("../../infra/supabase/volumes/db/vericov.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.components"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.repository_owner_rules"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.repository_package_nodes"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.component_coverage_rollups"));
        assertTrue(sql.contains("org_id uuid NOT NULL REFERENCES vericov.organizations"));
        assertTrue(sql.contains("component_id uuid NOT NULL REFERENCES vericov.components"));
        assertTrue(sql.contains("highest_active_risk_level text CHECK"));
        assertTrue(sql.contains("owners text[] NOT NULL DEFAULT ARRAY[]::text[]"));
        assertTrue(sql.contains("ALTER TABLE vericov.components ENABLE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("ALTER TABLE vericov.component_coverage_rollups ENABLE ROW LEVEL SECURITY"));
    }

    @Test
    void schemaDefinesCoverageGapFindingsTable() throws Exception {
        String sql = Files.readString(Path.of("../../infra/supabase/volumes/db/vericov.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.coverage_gap_findings"));
        assertTrue(sql.contains("risk_score numeric(5, 1) NOT NULL CHECK (risk_score >= 0 AND risk_score <= 100)"));
        assertTrue(sql.contains("risk_level text NOT NULL CHECK (risk_level IN ('critical', 'high', 'medium', 'low'))"));
        assertTrue(sql.contains("next_action text NOT NULL CHECK (next_action IN ('add_test', 'create_debt', 'mark_generated', 'inspect_instrumentation', 'run_source_explain'))"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS coverage_gap_findings_rank_idx"));
        assertTrue(sql.contains("ALTER TABLE vericov.coverage_gap_findings ENABLE ROW LEVEL SECURITY"));
    }
}
