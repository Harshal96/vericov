package dev.vericov.git.application.port;

public interface InternalServiceAuthorizer {
    String requireAuthorizedService(String serviceName, String serviceToken);
}
