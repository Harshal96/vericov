package dev.vericov.organization.application.port;

public interface InternalServiceAuthorizer {
    String requireAuthorizedService(String serviceName, String serviceToken);
}
