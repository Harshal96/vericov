# Coverage Agent Runner Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the initial metadata-only coverage-gap-to-agent-task handoff described in the L2 design.

**Architecture:** Add a new Agent Runner Control Plane Helidon service that owns internal task creation, policy decision recording, task reads, and event publication. The service follows existing Vericov patterns: API resource, application service, repository port, in-memory fallback, SQL schema, and Kong routing.

**Tech Stack:** Java 25, Helidon MP 4.4.1, JUnit 6, JSON-B, Supabase Postgres schema, Kong declarative routing.

---

### Task 1: Agent Task Domain and Service

**Files:**
- Create: `services/agent-runner/src/main/java/dev/vericov/agent/application/*`
- Create: `services/agent-runner/src/test/java/dev/vericov/agent/application/AgentControlPlaneServiceTest.java`

- [ ] Write failing tests that create `generate_tests` and `explain_gap` coverage-gap tasks, verify metadata-only payloads, event names, and policy decisions.
- [ ] Run `mvn -pl services/agent-runner test` and verify compilation/test failure because the module and classes do not exist.
- [ ] Implement immutable records, validation helpers, in-memory repository, and `AgentControlPlaneService`.
- [ ] Run `mvn -pl services/agent-runner test` and verify the service tests pass.

### Task 2: Internal API Contract

**Files:**
- Create: `services/agent-runner/src/main/java/dev/vericov/agent/api/*`
- Create: `services/agent-runner/src/test/java/dev/vericov/agent/api/InternalAgentTaskResourceTest.java`

- [ ] Write failing resource tests for `POST /internal/v1/agents/tasks`, `POST /internal/v1/agents/tasks/{task_id}/policy-decision`, and `GET /internal/v1/agents/tasks/{task_id}`.
- [ ] Verify the tests fail because API classes do not exist.
- [ ] Implement JSON-B request/response records, service-token authorization, validation error mapping, and response envelopes.
- [ ] Run `mvn -pl services/agent-runner test` and verify API tests pass.

### Task 3: Build, Schema, and Gateway Wiring

**Files:**
- Create: `services/agent-runner/pom.xml`
- Create: `services/agent-runner/src/main/java/dev/vericov/agent/Main.java`
- Create: `services/agent-runner/src/main/java/dev/vericov/agent/config/AgentRunnerComponents.java`
- Create: `services/agent-runner/src/main/resources/application.yaml`
- Modify: `pom.xml`
- Modify: `infra/supabase/volumes/db/vericov.sql`
- Modify: `infra/kong/kong.yml`
- Modify: `infra/kong/README.md`
- Modify: `infra/local/docker-compose.yml` if service orchestration currently lists backend services explicitly.

- [ ] Add module/build files and generated runtime configuration.
- [ ] Add `agent_runs`, `agent_tasks`, `policy_decisions`, and `agent_artifacts` tables.
- [ ] Route `/internal/v1/agents` to the new service and keep runner protocol routes as placeholders.
- [ ] Run `mvn test` and `node infra/kong/scripts/validate-config.mjs`.
