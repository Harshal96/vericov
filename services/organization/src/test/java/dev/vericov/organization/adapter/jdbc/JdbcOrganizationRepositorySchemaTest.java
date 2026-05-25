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
}
