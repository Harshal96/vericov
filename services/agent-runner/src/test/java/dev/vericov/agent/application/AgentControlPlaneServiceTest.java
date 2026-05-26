package dev.vericov.agent.application;

import dev.vericov.agent.application.port.AgentEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentControlPlaneServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REPORT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID GAP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID COMPONENT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final Instant NOW = Instant.parse("2026-05-25T10:00:00Z");

    @Test
    void createsMetadataOnlyCoverageGapTaskAndRecordsPolicyDecision() {
        RecordingEvents events = new RecordingEvents();
        AgentControlPlaneService service = new AgentControlPlaneService(
                new InMemoryAgentTaskRepository(),
                events,
                Clock.fixed(NOW, ZoneOffset.UTC));

        AgentTaskDetails task = service.createTask(command("generate_tests", "dry_run", RequestedBy.system("coverage-analysis")));

        assertEquals(TENANT_ID, task.tenantId());
        assertEquals(REPOSITORY_ID, task.repositoryId());
        assertEquals("generate_tests", task.taskType());
        assertEquals("dry_run", task.mode());
        assertEquals("queued", task.status());
        assertEquals("coverage_gap", task.source().type());
        assertEquals(REPORT_ID, task.source().coverageReportId());
        assertEquals(List.of(GAP_ID), task.source().coverageGapFindingIds());
        assertEquals("services/payments/discounts.ts", task.target().filePath());
        assertEquals("allow", task.policyDecision().decision());
        assertEquals("coverage_gap.generate_tests", task.policyDecision().action());
        assertEquals(List.of("agent.policy_decision.recorded", "agent.task.created"), events.names);
        assertFalse(task.payload().toString().contains("raw diff"));
        assertFalse(task.payload().toString().contains("source text"));
    }

    @Test
    void acceptsExplainGapCoverageGapTask() {
        AgentControlPlaneService service = new AgentControlPlaneService(
                new InMemoryAgentTaskRepository(),
                event -> {
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        AgentTaskDetails task = service.createTask(command("explain_gap", "suggest", RequestedBy.user("user-123")));

        assertEquals("explain_gap", task.taskType());
        assertEquals("suggest", task.mode());
        assertEquals("queued", task.status());
        assertEquals("allow", task.policyDecision().decision());
    }

    @Test
    void rejectsSourceBearingEvidenceInMetadataOnlyTask() {
        AgentControlPlaneService service = new AgentControlPlaneService(
                new InMemoryAgentTaskRepository(),
                event -> {
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        CreateAgentTaskCommand request = command(
                "generate_tests",
                "dry_run",
                RequestedBy.system("coverage-analysis"),
                Map.of("raw_diff_text", "@@ source text"));

        AgentRunnerException exception = assertThrows(AgentRunnerException.class, () -> service.createTask(request));

        assertEquals("validation_error", exception.code());
    }

    @Test
    void deniesSystemGeneratedTestsForActiveDebtSuppressedFinding() {
        AgentControlPlaneService service = new AgentControlPlaneService(
                new InMemoryAgentTaskRepository(),
                event -> {
                },
                Clock.fixed(NOW, ZoneOffset.UTC));

        CreateAgentTaskCommand request = command(
                "generate_tests",
                "dry_run",
                RequestedBy.system("coverage-analysis"),
                Map.of("debt_status", "active"));

        AgentRunnerException exception = assertThrows(AgentRunnerException.class, () -> service.createTask(request));

        assertEquals("policy_denied", exception.code());
    }

    private static CreateAgentTaskCommand command(String taskType, String mode, RequestedBy requestedBy) {
        return command(taskType, mode, requestedBy, Map.of());
    }

    private static CreateAgentTaskCommand command(
            String taskType,
            String mode,
            RequestedBy requestedBy,
            Map<String, Object> metadata) {
        return new CreateAgentTaskCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                taskType,
                mode,
                AgentTaskSource.coverageGap(REPORT_ID, List.of(GAP_ID), 42, "head456", "base123", "head456"),
                new AgentTaskTarget(
                        "services/payments/discounts.ts",
                        88,
                        94,
                        "high",
                        COMPONENT_ID,
                        List.of("team-payments")),
                new AgentTaskEvidence(
                        "new_uncovered_changed_line",
                        72.5,
                        "ctx-2026-05-25T10:00:00Z",
                        metadata),
                requestedBy);
    }

    private static final class RecordingEvents implements AgentEventPublisher {
        private final List<String> names = new ArrayList<>();

        @Override
        public void publish(AgentEvent event) {
            names.add(event.eventName());
        }
    }
}
