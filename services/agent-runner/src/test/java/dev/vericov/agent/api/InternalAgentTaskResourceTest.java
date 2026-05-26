package dev.vericov.agent.api;

import dev.vericov.agent.application.AgentControlPlaneService;
import dev.vericov.agent.application.InMemoryAgentTaskRepository;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InternalAgentTaskResourceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REPORT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID GAP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String SERVICE_NAME = "coverage-analysis";
    private static final String SERVICE_TOKEN = "service-token";

    @Test
    void createTaskReturnsCreatedEnvelopeForCoverageGapTask() {
        TestFixture fixture = new TestFixture();

        Response response = fixture.resource.createTask(SERVICE_NAME, SERVICE_TOKEN, request("generate_tests", "dry_run"));

        assertEquals(201, response.getStatus());
        AgentTaskHttpResponse body = responseBody(response, AgentTaskHttpResponse.class);
        assertEquals(REPOSITORY_ID, body.repositoryId());
        assertEquals("generate_tests", body.taskType());
        assertEquals("queued", body.status());
        assertEquals("allow", body.policyDecision().decision());
        assertEquals(List.of(GAP_ID), body.source().coverageGapFindingIds());
    }

    @Test
    void getTaskReturnsPreviouslyCreatedTask() {
        TestFixture fixture = new TestFixture();
        AgentTaskHttpResponse created = responseBody(
                fixture.resource.createTask(SERVICE_NAME, SERVICE_TOKEN, request("explain_gap", "suggest")),
                AgentTaskHttpResponse.class);

        Response response = fixture.resource.getTask(SERVICE_NAME, SERVICE_TOKEN, created.id().toString());

        assertEquals(200, response.getStatus());
        AgentTaskHttpResponse body = responseBody(response, AgentTaskHttpResponse.class);
        assertEquals(created.id(), body.id());
        assertEquals("explain_gap", body.taskType());
    }

    @Test
    void recordPolicyDecisionUpdatesTaskDecision() {
        TestFixture fixture = new TestFixture();
        AgentTaskHttpResponse created = responseBody(
                fixture.resource.createTask(SERVICE_NAME, SERVICE_TOKEN, request("generate_tests", "dry_run")),
                AgentTaskHttpResponse.class);

        Response response = fixture.resource.recordPolicyDecision(
                SERVICE_NAME,
                SERVICE_TOKEN,
                created.id().toString(),
                new RecordPolicyDecisionHttpRequest(
                        TENANT_ID.toString(),
                        REPOSITORY_ID.toString(),
                        "force_dry_run",
                        List.of(),
                        "coverage_gap.generate_tests",
                        Map.of("file_path", "services/payments/discounts.ts"),
                        "repository policy allows dry run only"));

        assertEquals(200, response.getStatus());
        AgentTaskHttpResponse body = responseBody(response, AgentTaskHttpResponse.class);
        assertEquals("force_dry_run", body.policyDecision().decision());
    }

    @Test
    void internalEndpointsRequireServiceIdentity() {
        TestFixture fixture = new TestFixture();

        assertError(fixture.resource.createTask(null, SERVICE_TOKEN, request("generate_tests", "dry_run")), 401, "unauthorized");
        assertError(fixture.resource.createTask(" ", SERVICE_TOKEN, request("generate_tests", "dry_run")), 401, "unauthorized");
        assertError(fixture.resource.createTask(SERVICE_NAME, "wrong-token", request("generate_tests", "dry_run")), 401, "unauthorized");
    }

    @Test
    void createTaskReturnsValidationErrorForMalformedUuid() {
        TestFixture fixture = new TestFixture();
        CreateAgentTaskHttpRequest request = request("generate_tests", "dry_run")
                .withTenantId("not-a-uuid");

        assertError(fixture.resource.createTask(SERVICE_NAME, SERVICE_TOKEN, request), 400, "validation_error");
    }

    @Test
    void createTaskReturnsValidationErrorForMissingNestedRequestPart() {
        TestFixture fixture = new TestFixture();
        CreateAgentTaskHttpRequest request = request("generate_tests", "dry_run")
                .withSource(null);

        assertError(fixture.resource.createTask(SERVICE_NAME, SERVICE_TOKEN, request), 400, "validation_error");
    }

    private static CreateAgentTaskHttpRequest request(String taskType, String mode) {
        return new CreateAgentTaskHttpRequest(
                TENANT_ID.toString(),
                ORG_ID.toString(),
                REPOSITORY_ID.toString(),
                taskType,
                mode,
                new AgentTaskSourceHttpRequest(
                        "coverage_gap",
                        REPORT_ID.toString(),
                        List.of(GAP_ID.toString()),
                        42,
                        "head456",
                        "base123",
                        "head456"),
                new AgentTaskTargetHttpRequest(
                        "services/payments/discounts.ts",
                        88,
                        94,
                        "high",
                        null,
                        List.of("team-payments")),
                new AgentTaskEvidenceHttpRequest(
                        "new_uncovered_changed_line",
                        72.5,
                        "ctx-2026-05-25T10:00:00Z",
                        Map.of()),
                new RequestedByHttpRequest("system", "coverage-analysis"));
    }

    private static <T> T responseBody(Response response, Class<T> type) {
        ApiResponse<?> envelope = assertInstanceOf(ApiResponse.class, response.getEntity());
        return assertInstanceOf(type, envelope.data());
    }

    private static void assertError(Response response, int status, String code) {
        assertEquals(status, response.getStatus());
        ApiError error = assertInstanceOf(ApiError.class, response.getEntity());
        assertEquals(code, error.error().code());
    }

    private static final class TestFixture {
        private final AgentControlPlaneService service = new AgentControlPlaneService(
                new InMemoryAgentTaskRepository(),
                event -> {
                },
                Clock.fixed(Instant.parse("2026-05-25T10:00:00Z"), ZoneOffset.UTC));
        private final InternalAgentTaskResource resource = new InternalAgentTaskResource(
                service,
                (serviceName, serviceToken) -> {
                    if (!SERVICE_NAME.equals(serviceName) || !SERVICE_TOKEN.equals(serviceToken)) {
                        throw new RuntimeException("bad service token");
                    }
                    return serviceName;
                });
    }
}
