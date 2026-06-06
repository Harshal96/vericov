package dev.vericov.controlplane.application.port;

public interface InternalServiceAuthorizer {
    String requireAuthorizedService(String serviceName, String serviceToken);
}
