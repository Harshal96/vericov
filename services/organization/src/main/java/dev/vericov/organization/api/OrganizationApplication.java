package dev.vericov.organization.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

@ApplicationScoped
@ApplicationPath("/")
public class OrganizationApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
                AuthResource.class,
                AuthorizationResource.class,
                InternalCoverageContextResource.class,
                InternalRepositoryConfigResource.class,
                RepositoryControlPlaneResource.class,
                OrganizationResource.class);
    }
}
