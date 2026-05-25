package dev.vericov.organization.api;

import java.util.List;

public record ApiError(ErrorBody error) {
    public static ApiError of(String code, String message) {
        return new ApiError(new ErrorBody(code, message, List.of()));
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
