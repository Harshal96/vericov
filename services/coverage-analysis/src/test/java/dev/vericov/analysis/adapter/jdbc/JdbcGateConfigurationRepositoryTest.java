package dev.vericov.analysis.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcGateConfigurationRepositoryTest {
    @Test
    void loadsActiveGateConfigurationsInNameOrder() {
        UUID tenantId = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
        UUID repositoryId = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
        JdbcProxySupport.RecordingDataSource dataSource = JdbcProxySupport.dataSource().whenSqlContains(
                "from vericov.repository_gate_configurations",
                new JdbcProxySupport.StatementBehavior().withRows(List.of(
                        JdbcProxySupport.row(
                                "id",
                                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                "tenant_id",
                                tenantId,
                                "repository_id",
                                repositoryId,
                                "name",
                                "line-minimum",
                                "gate_type",
                                "project_coverage",
                                "metric",
                                "line",
                                "threshold",
                                new BigDecimal("95.5"),
                                "max_drop",
                                null,
                                "blocking",
                                true,
                                "config_json",
                                "{\"mode\":\"strict\",\"labels\":[\"critical\"]}",
                                "status",
                                "active"))));
        JdbcGateConfigurationRepository repository = new JdbcGateConfigurationRepository(dataSource);

        var gates = repository.listActiveForRepository(tenantId, repositoryId);

        assertEquals(1, gates.size());
        assertEquals("line-minimum", gates.getFirst().name());
        assertEquals(new BigDecimal("95.5"), gates.getFirst().threshold());
        assertEquals(null, gates.getFirst().maxDrop());
        assertEquals(true, gates.getFirst().blocking());
        assertEquals(Map.of("mode", "strict", "labels", List.of("critical")), gates.getFirst().config());
        assertTrue(gates.getFirst().active());
    }

    @Test
    void wrapsSqlFailures() {
        UUID tenantId = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
        UUID repositoryId = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
        JdbcGateConfigurationRepository repository = new JdbcGateConfigurationRepository(
                JdbcProxySupport.dataSource().whenSqlContains(
                        "from vericov.repository_gate_configurations",
                        new JdbcProxySupport.StatementBehavior()
                                .withExecuteQueryException(new SQLException("broken query"))));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> repository.listActiveForRepository(tenantId, repositoryId));

        assertTrue(failure.getMessage().contains("Failed to load gate configuration for repository " + repositoryId));
        assertTrue(failure.getCause() instanceof SQLException);
    }
}
