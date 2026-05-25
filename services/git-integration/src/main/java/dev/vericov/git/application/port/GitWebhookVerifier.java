package dev.vericov.git.application.port;

public interface GitWebhookVerifier {
    boolean verify(String providerKey, String eventType, String deliveryId, String signature, byte[] payload);
}
