package dev.vericov.agent.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.agent.application.AgentRunnerException;
import dev.vericov.agent.application.InMemoryAgentTaskRepository;
import java.time.Clock;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AgentRunnerComponentsTest {
    @Test
    void defaultsToInMemoryRepositoryWhenNoDatabaseIsConfigured() {
        Assumptions.assumeTrue(env("VERICOV_DATABASE_URL").isBlank() && env("SUPABASE_DB_URL").isBlank());
        AgentRunnerComponents components = new AgentRunnerComponents();

        assertInstanceOf(InMemoryAgentTaskRepository.class, components.agentTaskRepository());
    }

    @Test
    void wiresApplicationScopedDefaults() {
        AgentRunnerComponents components = new AgentRunnerComponents();

        assertNotNull(components.agentControlPlaneService(
                new InMemoryAgentTaskRepository(),
                event -> {
                },
                Clock.systemUTC()));
        assertDoesNotThrow(() -> components.agentEventPublisher().publish(null));
        assertNotNull(components.clock().instant());
    }

    @Test
    void internalServiceAuthorizerFailsClosedWithoutConfiguredHashes() {
        Assumptions.assumeTrue(env("VERICOV_INTERNAL_SERVICE_TOKEN_SHA256").isBlank());
        AgentRunnerComponents components = new AgentRunnerComponents();

        AgentRunnerException exception = assertThrows(
                AgentRunnerException.class,
                () -> components.internalServiceAuthorizer().requireAuthorizedService("coverage-analysis", "token"));

        assertEquals("unauthorized", exception.code());
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
