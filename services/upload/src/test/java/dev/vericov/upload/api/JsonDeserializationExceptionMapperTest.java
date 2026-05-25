package dev.vericov.upload.api;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JsonDeserializationExceptionMapperTest {

    @Test
    void mapsJsonBindingFailuresToValidationEnvelope() {
        JsonDeserializationExceptionMapper mapper = new JsonDeserializationExceptionMapper();
        ProcessingException exception = new ProcessingException(
                "Error deserializing object from entity stream.",
                new JsonbException("Internal error: Invalid UUID string: "));

        Response response = mapper.toResponse(exception);

        assertEquals(400, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("validation_error", error.error().code());
        assertEquals("Request body is invalid JSON or contains invalid field values", error.error().message());
    }
}
