# Vericov BDD and Integration Testing Plan

## Framework Recommendation

Use a layered testing stack:

- JUnit Jupiter remains the default for domain, parser, application-service, and adapter unit tests.
- Cucumber JVM with the JUnit Platform engine provides executable BDD specs for product-readable acceptance behavior.
- Helidon MicroProfile JUnit 5 testing covers CDI wiring, resource registration, configuration, and in-process HTTP/API behavior.
- Testcontainers Java covers real PostgreSQL, queue, and object-storage-adjacent integration where fake ports would hide risk.

Do not force every test through Cucumber. BDD should describe behavior a stakeholder or API consumer would recognize; integration tests should prove infrastructure boundaries.

## Maven Layout

Current service modules inherit from the Helidon MP parent directly, so shared dependency management in the root aggregator does not automatically apply to them. Add test dependencies to the service module that needs them unless a separate Vericov parent POM is introduced.

For BDD in a Java service module, add current versions of:

- `io.cucumber:cucumber-java`
- `io.cucumber:cucumber-junit-platform-engine`
- `org.junit.platform:junit-platform-suite`

For Helidon MP tests, add:

- `io.helidon.microprofile.testing:helidon-microprofile-testing-junit5`
- JUnit Jupiter engine support if the module does not already inherit it

For container-backed integration tests, add:

- `org.testcontainers:testcontainers`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:postgresql`

Use Cucumber and Testcontainers BOMs inside each service module when Maven does not already provide compatible managed versions. Verify exact current dependency coordinates before editing POMs.

## Suggested File Layout

```text
services/upload/src/test/resources/features/upload/upload-ingestion.feature
services/upload/src/test/java/dev/vericov/upload/bdd/RunUploadFeaturesTest.java
services/upload/src/test/java/dev/vericov/upload/bdd/steps/UploadSteps.java
services/upload/src/test/java/dev/vericov/upload/bdd/support/UploadScenarioContext.java
services/upload/src/test/java/dev/vericov/upload/api/UploadResourceIntegrationTest.java
services/upload/src/test/java/dev/vericov/upload/adapter/storage/SupabaseStorageIntegrationTest.java

services/coverage-analysis/src/test/resources/features/analysis/coverage-analysis.feature
services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/RunAnalysisFeaturesTest.java
services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/steps/AnalysisSteps.java
services/coverage-analysis/src/test/java/dev/vericov/analysis/bdd/support/AnalysisScenarioContext.java
services/coverage-analysis/src/test/java/dev/vericov/analysis/adapter/jdbc/JdbcAnalysisJobRepositoryIntegrationTest.java
```

## Upload Ingestion Scenarios

Cover these behaviors as BDD acceptance scenarios when upload API or application behavior changes:

- Authorized repository API key submits coverage and test-result artifacts and receives `202 Accepted`, upload status `queued`, and a poll URL.
- Repeating the same idempotency key returns the existing upload and does not duplicate artifacts, events, or queue jobs.
- Idempotent retry is still authenticated and authorized before the existing upload is returned.
- API key without `uploads:create` is rejected and no artifact, event, or queue side effect occurs.
- Branch restrictions reject uploads for disallowed branches.
- Unsafe artifact names such as path traversal are rejected before storage.
- Invalid or unsupported artifact kind, format, content type, missing content, or malformed base64 is rejected with a stable error envelope.
- Upload status returns stored artifact metadata and tenant/repository-scoped state only.
- Storage failure, event publish failure, or queue failure results in a predictable failure mode without partial client success.

Integration tests should cover:

- Helidon resource wiring, JSON request/response envelopes, auth header parsing, status codes, and OpenAPI-visible API shape.
- Supabase storage bucket mapping, object path construction, content length/hash metadata, and remote error translation.
- Repository persistence if the in-memory upload repository is replaced with JDBC.
- Event/queue insertion contract consumed by coverage analysis.

## Coverage Analysis Scenarios

Cover these behaviors as BDD acceptance scenarios when analysis behavior changes:

- `upload.received` downloads all coverage artifacts, merges LCOV metrics, and persists a report for the upload, tenant, repository, commit, branch, and PR.
- Multiple LCOV artifacts for the same file merge line and branch coverage correctly.
- Upload with no coverage artifacts fails with a classified message and does not persist a misleading report.
- Malformed LCOV or missing object storage content follows the intended retry/dead-letter policy.
- Already-completed or already-locked jobs are archived or rescheduled without duplicate processing.
- Processing failure records the job failure and either reschedules or dead-letters based on attempts.
- Unsupported event types are moved to dead letter with an auditable reason.
- Worker identity and fixed clock values are recorded consistently.

Integration tests should cover:

- JDBC job start locking, complete/failure transitions, visibility timeouts, reschedule/archive/dead-letter queue operations.
- JSON message codec compatibility between upload ingestion and coverage analysis.
- Coverage input repository loading artifacts written by upload ingestion.
- Coverage report repository insert/update behavior and tenant isolation.

## Cross-Service and Infrastructure Scenarios

Add these once the corresponding modules or infrastructure are executable in CI:

- Kong routes upload API requests to the upload service and preserves required auth/idempotency headers.
- Supabase schema migration creates required tenants, repositories, API keys, queues, buckets, policies, and indexes.
- Upload ingestion event payload is accepted by coverage analysis without field drift.
- Future Git integration receives a coverage report and posts or updates commit/PR status exactly once.
- Future agent runner receives low-confidence coverage gaps and writes bounded remediation jobs without leaking tenant data.

## Scenario Mining Heuristics

Use these prompts while reading new code:

- What new public method, endpoint, event type, enum value, SQL table, config key, or adapter port appeared?
- What client-visible status code, error code, response field, persisted state, queue message, or object path changed?
- What validation moved closer to or farther from a trust boundary?
- What could happen twice, concurrently, out of order, or after a retry?
- What external service can fail, time out, return malformed data, or partially succeed?
- What tenant, repository, branch, API key, or scope boundary protects this behavior?
- What existing unit test proves internals but leaves the real integration contract untested?

## BDD Authoring Rules

Use feature names that describe capabilities, not classes. Prefer:

- `Feature: Upload coverage artifacts`
- `Feature: Analyze uploaded coverage`
- `Feature: Manage analysis job retries`

Avoid:

- `Feature: UploadApplicationService`
- `Feature: JdbcAnalysisJobRepository`

Keep `Given` steps about world state, `When` steps about a single actor action, and `Then` steps about observable outcomes. Move implementation details into step definitions and support fixtures.

## CI Shape

Recommended eventual Maven commands:

```bash
mvn test
mvn -pl services/upload test -Dcucumber.filter.tags="@upload and not @wip"
mvn -pl services/coverage-analysis test -Dcucumber.filter.tags="@analysis and not @wip"
mvn -pl services/upload,services/coverage-analysis test -Dgroups=integration
```

Keep Docker/Testcontainers tests clearly tagged or named so fast unit/BDD checks can run without containers when needed.
