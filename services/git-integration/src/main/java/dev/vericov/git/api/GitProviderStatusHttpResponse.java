package dev.vericov.git.api;

import java.util.List;

public record GitProviderStatusHttpResponse(
        List<GitProviderStatus> providers) {

    public record GitProviderStatus(
            String providerKey,
            String executionStatus,
            String webhookStatus,
            String installationStatus) {
    }
}
