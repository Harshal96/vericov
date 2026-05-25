package dev.vericov.upload.api;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JsonDeserializationExceptionMapper implements ExceptionMapper<ProcessingException> {
    private static final String MESSAGE = "Request body is invalid JSON or contains invalid field values";

    @Override
    public Response toResponse(ProcessingException exception) {
        if (!hasCause(exception, JsonbException.class)) {
            return Response.serverError()
                    .entity(ApiError.of("internal_error", "Internal request processing failed"))
                    .build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiError.of("validation_error", MESSAGE))
                .build();
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
