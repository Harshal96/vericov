package dev.vericov.agent.api;

public record ApiError(ErrorBody error) {
    public static ApiError of(String code, String message) {
        return new ApiError(new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}
