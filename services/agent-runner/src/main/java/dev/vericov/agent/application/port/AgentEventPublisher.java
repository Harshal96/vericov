package dev.vericov.agent.application.port;

import dev.vericov.agent.application.AgentEvent;

@FunctionalInterface
public interface AgentEventPublisher {
    void publish(AgentEvent event);
}
