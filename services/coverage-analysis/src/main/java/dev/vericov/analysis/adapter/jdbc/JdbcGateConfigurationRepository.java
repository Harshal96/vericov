package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.gates.GateConfiguration;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcGateConfigurationRepository implements GateConfigurationRepository {
    private final DataSource dataSource;
    private final AnalysisJsonCodec codec = new AnalysisJsonCodec();

    public JdbcGateConfigurationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, repository_id, name, gate_type, metric,
                               threshold, max_drop, blocking, config_json, status
                        from vericov.repository_gate_configurations
                        where tenant_id = ?
                          and repository_id = ?
                          and status = 'active'
                        order by name, id
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<GateConfiguration> gates = new ArrayList<>();
                while (resultSet.next()) {
                    gates.add(new GateConfiguration(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getObject("tenant_id", UUID.class),
                            resultSet.getObject("repository_id", UUID.class),
                            resultSet.getString("name"),
                            resultSet.getString("gate_type"),
                            resultSet.getString("metric"),
                            resultSet.getBigDecimal("threshold"),
                            resultSet.getBigDecimal("max_drop"),
                            resultSet.getBoolean("blocking"),
                            codec.jsonObject(resultSet, "config_json"),
                            resultSet.getString("status")));
                }
                return List.copyOf(gates);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load gate configuration for repository " + repositoryId, exception);
        }
    }
}
