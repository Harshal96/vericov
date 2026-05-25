package dev.vericov.git.adapter.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcGitActionRepositoryTest {
    @Test
    void schemaDefinesGitOwnedActionTables() throws Exception {
        String sql = Files.readString(Path.of("../../infra/supabase/volumes/db/vericov.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_webhook_events"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_pull_requests"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_check_runs"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_pr_comments"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_pr_annotations"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_branches"));
        assertTrue(sql.contains("ALTER TABLE vericov.git_webhook_events ENABLE ROW LEVEL SECURITY"));
    }
}
