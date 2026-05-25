package dev.vericov.integrations.application.port;

public interface InternalServiceAuthorizer {
    String requireAuthorizedService(String serviceName, String serviceToken);
}
