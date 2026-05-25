package dev.vericov.git.api;

import dev.vericov.git.application.CreateOrUpdateCheckRunCommand;
import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;
import dev.vericov.git.application.GitProviderActionService;
import dev.vericov.git.application.port.GitProviderActionPort;
import dev.vericov.git.application.port.IntegrationConfigClient;
import dev.vericov.git.application.port.ResolvedGitIntegration;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InternalGitResourceTest {
    @Test
    void createCheckRunReturnsAcceptedEnvelope() {
        RecordingGitProviderActionService service = new RecordingGitProviderActionService();
        InternalGitResource resource = new InternalGitResource(service, (serviceName, serviceToken) -> serviceName);

        Response response = resource.createCheckRun(
                "coverage-analysis",
                "service-token",
                new CreateCheckRunHttpRequest(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "github",
                        "abc123",
                        "Vericov Coverage",
                        "completed",
                        "success",
                        "Coverage passed",
                        "Patch coverage passed",
                        "https://app.vericov.dev/reports/1",
                        List.of(),
                        "coverage-abc123"));

        assertEquals(202, response.getStatus());
        assertEquals(1, service.checkRunCalls);
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        GitActionHttpResponse body = assertInstanceOf(GitActionHttpResponse.class, envelope.data());
        assertEquals("accepted", body.status());
    }

    @Test
    void createCheckRunRejectsInvalidUuid() {
        InternalGitResource resource = new InternalGitResource(new RecordingGitProviderActionService(),
                (serviceName, serviceToken) -> serviceName);

        Response response = resource.createCheckRun(
                "coverage-analysis",
                "service-token",
                new CreateCheckRunHttpRequest(
                        "not-a-uuid",
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "github",
                        "abc123",
                        "Vericov Coverage",
                        "completed",
                        "success",
                        null,
                        null,
                        null,
                        List.of(),
                        "coverage-abc123"));

        assertEquals(400, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals("validation_error", error.error().code());
    }

    private static final class RecordingGitProviderActionService extends GitProviderActionService {
        private int checkRunCalls;

        private RecordingGitProviderActionService() {
            super(new NoopIntegrationConfigClient(), new NoopGitProviderActionPort());
        }

        @Override
        public void createOrUpdateCheckRun(CreateOrUpdateCheckRunCommand command) {
            checkRunCalls++;
        }
    }

    private static final class NoopIntegrationConfigClient implements IntegrationConfigClient {
        @Override
        public ResolvedGitIntegration resolveRepositoryIntegration(
                UUID tenantId,
                UUID orgId,
                UUID repositoryId,
                String providerKey,
                String capability) {
            throw new UnsupportedOperationException();
        }

        @Override
        public dev.vericov.git.application.port.CredentialLease leaseCredential(
                UUID tenantId,
                UUID orgId,
                UUID connectionId,
                String credentialKind,
                String serviceName) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoopGitProviderActionPort implements GitProviderActionPort {
        @Override
        public GitProviderActionResult execute(GitProviderAction action) {
            return new GitProviderActionResult(action.type(), "provider-id", "completed", null, Map.of());
        }
    }
}
