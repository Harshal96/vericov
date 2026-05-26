package dev.vericov.agent.adapter.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAgentTaskRepositoryTest {
    @Test
    void schemaDefinesAgentTaskLifecycleTables() throws Exception {
        String sql = Files.readString(Path.of("../../infra/supabase/volumes/db/vericov.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.agent_runs"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.agent_tasks"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.policy_decisions"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.agent_artifacts"));
        assertTrue(sql.contains("ALTER TABLE vericov.agent_runs ENABLE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS policy_decisions_agent_task_idx"));
    }
}
