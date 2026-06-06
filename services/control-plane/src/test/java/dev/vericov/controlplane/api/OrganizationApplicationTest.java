package dev.vericov.controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OrganizationApplicationTest {

    @Test
    void registersOnlyRepositoryAndInternalResources() {
        assertEquals(
                Set.of(
                        InternalCoverageContextResource.class,
                        InternalRepositoryConfigResource.class,
                        RepositoryControlPlaneResource.class),
                new OrganizationApplication().getClasses());
    }

    @Test
    void repositoryResourceRetainsRepositoryCrudHandlers() {
        assertEndpoint("listRepositories", GET.class, "/{org_id}/repositories");
        assertEndpoint("registerRepository", POST.class, "/{org_id}/repositories");
        assertEndpoint("getRepository", GET.class, "/{org_id}/repositories/{repository_id}");
        assertEndpoint("updateRepository", PATCH.class, "/{org_id}/repositories/{repository_id}");
    }

    private static void assertEndpoint(
            String methodName,
            Class<? extends Annotation> httpMethod,
            String path) {
        Method method = Stream.of(RepositoryControlPlaneResource.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        assertTrue(method.isAnnotationPresent(httpMethod));
        assertEquals(path, method.getAnnotation(Path.class).value());
    }
}
