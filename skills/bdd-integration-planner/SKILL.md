---
name: bdd-integration-planner
description: Plan and generate behavior-driven development (BDD) and integration test coverage from changed or newly added code. Use when Codex needs to inspect diffs or new code, discover user-visible scenarios, choose Cucumber/Gherkin versus integration test layers, create or update .feature files, add step definitions, add Helidon/JUnit/Testcontainers integration tests, or propose a BDD and integration testing framework for Vericov services.
---

# BDD Integration Planner

## Overview

Use this skill to turn changed code into executable behavior coverage. Read the new code, identify the behaviors and risks it introduces, then write the smallest useful set of BDD scenarios and integration tests.

Load `references/vericov-bdd-integration-plan.md` when the task asks for framework choices, scenario coverage, module layout, or Vericov-specific examples.

## Workflow

1. Discover the change surface.
   - Inspect `git status --short`.
   - Prefer staged changes with `git diff --name-status --staged`; otherwise use `git diff --name-status HEAD`.
   - If there is no working diff, compare the feature branch to the mainline or ask for the code range.
   - Read changed production files, adjacent tests, relevant docs, and service POMs.

2. Mine behavior scenarios from code evidence.
   - Identify APIs, commands, application services, ports, adapters, events, queues, persistence, validation, auth, idempotency, retries, and error envelopes.
   - Extract externally observable rules from method names, request/response records, exception codes, enum states, event types, SQL, config keys, and existing tests.
   - Do not infer a product promise from an internal helper alone; tie every scenario to a public API, message contract, persisted outcome, or documented business rule.

3. Build a scenario inventory before editing tests.

   ```markdown
   | Behavior | Evidence | Risk | Layer | Artifact |
   | --- | --- | --- | --- | --- |
   | Authorized upload queues analysis once | UploadResource + UploadApplicationService | API contract/idempotency | BDD + HTTP integration | upload-acceptance.feature |
   ```

4. Choose the test layer.
   - Use BDD/Gherkin for business-facing flows, API contracts, cross-service behaviors, and security/idempotency rules that a product stakeholder should read.
   - Use integration tests for technical boundaries: Helidon HTTP/CDI wiring, JDBC repositories, Supabase storage clients, queues, object storage, Dockerized dependencies, and serialization.
   - Keep algorithmic edge cases in focused JUnit unit tests unless they represent product-visible behavior.

5. Write or update BDD first for behavior changes.
   - Put feature files under `services/<service>/src/test/resources/features/<domain>/<capability>.feature`.
   - Put Cucumber runners under `services/<service>/src/test/java/dev/vericov/<service>/bdd/`.
   - Put step definitions under `.../bdd/steps/` and shared fixtures under `.../bdd/support/`.
   - Tag scenarios by capability and speed, for example `@upload`, `@analysis`, `@api`, `@integration`, `@security`, `@idempotency`, `@wip`.

6. Add integration tests where BDD steps need real boundaries.
   - For Helidon MicroProfile HTTP/CDI tests, prefer Helidon MP JUnit 5 test support.
   - For PostgreSQL, queue, or storage integration, prefer Testcontainers with disposable local services.
   - Use fixed clocks, generated UUIDs, isolated tenants/repositories, deterministic object keys, and no live external network.

7. Verify.
   - Run the narrowest Maven module test first, such as `mvn -pl services/upload test`.
   - Run targeted Cucumber tags when available, such as `mvn -pl services/upload test -Dcucumber.filter.tags="@upload and not @wip"`.
   - If dependencies or framework APIs are added, verify current docs first, preferably through Context7 for Cucumber JVM, Helidon, and Testcontainers.

## Scenario Quality Bar

Every changed behavior should usually have:

- One happy-path scenario that proves the user-visible outcome.
- One validation or malformed-input scenario for each new boundary.
- One authorization or tenant-isolation scenario when identity, scopes, branches, or repository ownership matter.
- One idempotency, retry, concurrency, or duplicate-message scenario when queues, jobs, or external calls are involved.
- One persistence or external-adapter integration test when data durability, SQL, storage paths, object metadata, or queue visibility are part of the behavior.
- One error-envelope or failure-classification scenario when clients or workers depend on error codes.

Avoid scenarios that merely restate implementation. Prefer "Then the upload is queued once" over "Then `enqueueAnalysis` is called".

## Gherkin Style

Use declarative language and keep implementation details in step definitions.

```gherkin
@upload @api @idempotency
Feature: Upload coverage artifacts

  Rule: Accepted uploads are queued for analysis exactly once

    Scenario: Authorized repository upload queues analysis
      Given repository "payments-api" accepts uploads on branch "main"
      And the request includes coverage and test-result artifacts
      When the repository submits the upload with idempotency key "upload-1"
      Then the API accepts the upload
      And the response contains a poll URL
      And coverage analysis is queued once
```

Use `Scenario Outline` only when the same rule is exercised with different examples. Prefer separate scenarios when outcomes, risks, or setup differ.

## Output Expectations

When asked to add BDD for new code:

1. Summarize the changed behavior inventory.
2. Create or update feature files before glue code.
3. Add step definitions and integration fixtures only for selected scenarios.
4. Keep test helpers close to the service module using them.
5. Report the exact test command run and any remaining unverified risk.
