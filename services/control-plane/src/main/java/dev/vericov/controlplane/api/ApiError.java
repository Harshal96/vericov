package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.OrganizationException;
import jakarta.ws.rs.core.Response;
import java.util.List;

public record ApiError(ErrorBody error) {
    public static ApiError of(String code, String message) {
        return new ApiError(new ErrorBody(code, message, List.of()));
    }

    public static Response response(OrganizationException exception) {
        Response.Status status = switch (exception.code()) {
            case "unauthorized" -> Response.Status.UNAUTHORIZED;
            case "forbidden" -> Response.Status.FORBIDDEN;
            case "not_found" -> Response.Status.NOT_FOUND;
            case "conflict" -> Response.Status.CONFLICT;
            default -> Response.Status.BAD_REQUEST;
        };
        return Response.status(status)
                .entity(of(exception.code(), exception.getMessage()))
                .build();
    }

    public record ErrorBody(
            String code,
            String message,
            List<FieldError> details) {

        public ErrorBody {
            details = List.copyOf(details == null ? List.of() : details);
        }
    }

    public record FieldError(
            String field,
            String code,
            String message) {
    }
}
