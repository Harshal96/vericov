package dev.vericov.upload.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

@ApplicationScoped
@ApplicationPath("/")
public class UploadApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
                UploadResource.class,
                JsonDeserializationExceptionMapper.class);
    }
}
