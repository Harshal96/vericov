package dev.vericov.integrations.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

@ApplicationScoped
@ApplicationPath("/")
public class IntegrationsApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(IntegrationResource.class, InternalIntegrationResource.class);
    }
}
