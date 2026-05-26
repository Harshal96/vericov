# Agent Runner Service

The Agent Runner service is Vericov's internal control plane for agent tasks and
policy decisions. It records work requests that will eventually be leased by
self-hosted runners, and it lets trusted services attach policy decisions and
evidence to those tasks.

The broader runner fleet API is described in
`docs/backend/services/06-agent-runner-control-plane-service.md`. The code in
this service currently implements the internal task API first.

## Why This Service Exists

Coverage analysis can discover high-risk uncovered code, but source-bearing
repair work should be isolated from the coverage worker and, in enterprise
deployments, often needs to run inside a customer's network. This service gives
Vericov a durable boundary for:

- creating an agent task from another service event;
- recording who requested it and what code target it may touch;
- recording policy decisions before a runner does any work;
- exposing task state back to trusted internal callers.

## Current Architecture

```text
                    internal service token
                           |
                           v
+-------------------+  HTTP /internal/v1/agents  +----------------------+
| coverage-analysis | --------------------------> | InternalAgentTask    |
| organization      |                             | Resource             |
| git-integration   |                             +----------+-----------+
+-------------------+                                        |
                                                             v
                                                  +----------+-----------+
                                                  | AgentControlPlane    |
                                                  | Service              |
                                                  +----------+-----------+
                                                             |
                          +----------------------------------+-------------------+
                          |                                                      |
                          v                                                      v
              +-----------+------------+                             +-----------+-----------+
              | AgentTaskRepository    |                             | AgentEventPublisher   |
              | JDBC or in-memory      |                             | currently no-op/dev   |
              +------------------------+                             +-----------------------+
```

## Where It Is Called From

```text
Coverage gap found
  -> coverage-analysis decides an agent task is useful
  -> POST /internal/v1/agents/tasks
  -> agent-runner stores task with source, target, requester, evidence

Policy evaluated
  -> policy/control-plane service decides dry-run or PR permissions
  -> POST /internal/v1/agents/tasks/{task_id}/policy-decision
  -> agent-runner appends decision details to task state

Task status read
  -> internal UI/control-plane or worker coordinator
  -> GET /internal/v1/agents/tasks/{task_id}
  -> agent-runner returns the canonical task record
```

Planned runner protocol calls such as `/runner/v1/register`,
`/runner/v1/tasks/lease`, and task completion are documented but not yet wired
in this module.

## Data Model

Primary concepts in `src/main/java/dev/vericov/agent/application`:

| Concept | What It Represents |
| --- | --- |
| `AgentTaskDetails` | The durable task record returned by the API |
| `CreateAgentTaskCommand` | Validated request to create a task |
| `AgentTaskSource` | Event or system that requested the task |
| `AgentTaskTarget` | Repository, commit, PR, file, and line target |
| `AgentTaskEvidence` | Finding or report evidence that justified the task |
| `RequestedBy` | User, service, policy, or automation requester |
| `PolicyDecisionDetails` | Permission result such as allowed mode and reason |
| `AgentEvent` | Domain event emitted as task state changes |

Expected persistence shape:

```text
agent_tasks
  id
  tenant_id
  organization_id
  repository_id
  task_type
  mode
  status
  source_json
  target_json
  evidence_json
  requested_by_json
  policy_decision_json
  created_at
  updated_at

agent_task_events
  id
  task_id
  event_type
  payload_json
  created_at
```

The JDBC adapter stores JSON subdocuments through `AgentJsonCodec`; tests also
use `InMemoryAgentTaskRepository` for fast application and resource coverage.

## APIs

Internal callers must include service authentication headers:

```text
X-Vericov-Service-Name: <service>
X-Vericov-Service-Token: <token>
```

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/agents/tasks` | Create an agent task |
| `GET` | `/internal/v1/agents/tasks/{task_id}` | Read task state |
| `POST` | `/internal/v1/agents/tasks/{task_id}/policy-decision` | Attach latest policy decision |

Responses use the service-local envelope:

```json
{
  "data": {
    "id": "85d7a880-91c1-465d-885b-2d2e325abed8",
    "task_type": "generate_tests",
    "mode": "dry_run",
    "status": "queued"
  }
}
```

## Source Map

```text
src/main/java/dev/vericov/agent/api
  Internal JAX-RS resource and HTTP request/response records

src/main/java/dev/vericov/agent/application
  Task creation, validation, state transitions, in-memory repository

src/main/java/dev/vericov/agent/application/port
  Repository, event publisher, and internal service auth ports

src/main/java/dev/vericov/agent/adapter/jdbc
  JDBC persistence and JSON encoding

src/main/java/dev/vericov/agent/config
  Helidon/CDI wiring for production and local development
```

## Tests

```text
src/test/java/dev/vericov/agent/application
  Service-level task and policy behavior

src/test/java/dev/vericov/agent/api
  Internal resource status codes, auth, and request validation

src/test/java/dev/vericov/agent/adapter/jdbc
  Schema-oriented JDBC coverage plus JSON codec behavior

src/test/java/dev/vericov/agent/config
  Local component wiring and fail-closed internal auth behavior
```

Run this service only:

```bash
mvn -pl services/agent-runner test
```
