package dev.vericov.componentconfig;

public class ComponentConfigException extends IllegalArgumentException {
    public ComponentConfigException(String message) {
        super(message);
    }

    public ComponentConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
