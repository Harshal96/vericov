# Policy and Gate Configuration APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add organization-owned, repository-scoped policy and gate configuration APIs to the Organization / API Control Plane service.

**Architecture:** The Organization service owns durable configuration because policies and gates are controlled by org membership, repository registration, and UI/API control-plane workflows. Public APIs stay fully org-scoped under `/api/v1/orgs/{org_id}/repositories/{repository_id}` subpaths; Coverage Analysis later consumes a resolved effective config through an internal endpoint and performs actual gate evaluation. This plan stores immutable config snapshots with schema validation, separates org defaults from repository overrides, and avoids top-level repository routes.

**Tech Stack:** Java 25, Helidon 4 MP, JAX-RS, JSON-B, JUnit 5, Cucumber JVM, Supabase Postgres in the `vericov` schema, JDBC and in-memory repository adapters.

---

## Scope

Build these configuration surfaces in `services/organization`:

- Organization policy defaults.
- Repository UI-managed config override.
- Repository policy records.
- Repository gate configuration records.
- Effective repository configuration resolution.
- Validation APIs that normalize input and return field-level errors.
- Internal effective-config endpoint for service callers.

Do not implement actual coverage gate evaluation in this plan. Coverage Analysis will evaluate coverage reports against the effective gate config in a separate plan.

## API Shape

Public endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/policy-defaults` | Return org-level policy defaults |
| `PUT` | `/api/v1/orgs/{org_id}/policy-defaults` | Replace org-level policy defaults |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config` | Return effective repository config |
| `PUT` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config` | Store UI-managed repository config override |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config/validate` | Validate repository config without storing |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/policies` | List repository policies |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/policies` | Create repository policy |
| `PATCH` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/policies/{policy_id}` | Update repository policy |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates` | List repository gates |
| `PUT` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates` | Replace repository gates |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates/validate` | Validate gate config without storing |

Internal endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/internal/v1/orgs/{org_id}/repositories/{repository_id}/effective-config` | Return resolved config for Coverage Analysis, Git Integration, and Agent Runner callers |

## Authorization Rules

- Active org members can read repository config, policies, gates, and effective config.
- Org owners/admins can create/update policy defaults, repository config, policies, and gates.
- Viewers, developers, and auditors cannot mutate these resources.
- Public routes authenticate through the existing `UserPrincipalResolver`.
- Internal routes should initially use the same resolver in local tests, then move to service-token auth when service auth is introduced.

Add these action names to `OrganizationApplicationService`:

```java
repositories.config.read
repositories.config.update
repositories.policies.read
repositories.policies.update
repositories.gates.read
repositories.gates.update
org.policy_defaults.read
org.policy_defaults.update
```

## Data Model

Add these tables to `infra/supabase/volumes/db/vericov.sql`:

- `vericov.organization_policy_defaults`
- `vericov.repository_configs`
- `vericov.repository_policies`
- `vericov.repository_gate_configurations`

All tables use UUID primary keys, tenant/org/repository scoping where applicable, `created_at`, `updated_at`, RLS enabled, and no grants to `anon` or `authenticated`.

Use JSONB only for flexible config payloads. Keep lifecycle and authorization fields relational.

## Validation Rules

Config JSON:

- Must be an object.
- Top-level keys may contain lowercase letters, numbers, `_`, `-`, and `.`.
- Nested map keys follow the same rule.
- Values may be JSON scalar, array, or object.
- `schema_version` must be positive.
- Store defensive deep copies in all application records.

Policy records:

- `name`: 1-120 characters.
- `policy_type`: `coverage`, `mutation`, `agent_review`, `waiver`.
- `target_type`: `repository`, `component`, `path`.
- `target_selector`: required for `component` and `path`, optional for `repository`.
- `status`: `active`, `disabled`.
- `priority`: 0-1000.

Gate records:

- `name`: 1-120 characters.
- `gate_type`: `project_coverage`, `patch_coverage`, `coverage_drop`, `component_coverage`, `mutation_score`, `agent_review_required`.
- `metric`: `line`, `branch`, `function`, `statement`, `mutation`, `risk`.
- `threshold`: optional decimal for `agent_review_required`, required for coverage/mutation gates.
- `max_drop`: optional decimal for drop gates.
- `blocking`: boolean.
- `status`: `active`, `disabled`.

---

## File Structure

Create:

- `services/organization/src/main/java/dev/vericov/organization/application/ConfigurationValues.java`: deep-copy and validation helpers for JSON-like maps/lists.
- `services/organization/src/main/java/dev/vericov/organization/application/PolicyDefaultsDetails.java`: immutable org defaults.
- `services/organization/src/main/java/dev/vericov/organization/application/RepositoryConfigDetails.java`: immutable repository config override.
- `services/organization/src/main/java/dev/vericov/organization/application/RepositoryPolicyDetails.java`: immutable policy record.
- `services/organization/src/main/java/dev/vericov/organization/application/RepositoryGateDetails.java`: immutable gate record.
- `services/organization/src/main/java/dev/vericov/organization/application/EffectiveRepositoryConfig.java`: resolved config returned to API/internal callers.
- `services/organization/src/main/java/dev/vericov/organization/application/UpsertPolicyDefaultsCommand.java`
- `services/organization/src/main/java/dev/vericov/organization/application/UpsertRepositoryConfigCommand.java`
- `services/organization/src/main/java/dev/vericov/organization/application/ValidateRepositoryConfigCommand.java`
- `services/organization/src/main/java/dev/vericov/organization/application/CreateRepositoryPolicyCommand.java`
- `services/organization/src/main/java/dev/vericov/organization/application/UpdateRepositoryPolicyCommand.java`
- `services/organization/src/main/java/dev/vericov/organization/application/UpsertRepositoryGatesCommand.java`
- `services/organization/src/main/java/dev/vericov/organization/api/PolicyDefaultsHttpRequest.java`
- `services/organization/src/main/java/dev/vericov/organization/api/PolicyDefaultsHttpResponse.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryConfigHttpRequest.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryConfigHttpResponse.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryConfigValidationHttpResponse.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryPolicyHttpRequest.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryPolicyHttpResponse.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryGateHttpRequest.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryGateHttpResponse.java`
- `services/organization/src/main/java/dev/vericov/organization/api/RepositoryControlPlaneResource.java`
- `services/organization/src/main/java/dev/vericov/organization/api/InternalRepositoryConfigResource.java`

Modify:

- `services/organization/src/main/java/dev/vericov/organization/application/OrganizationApplicationService.java`
- `services/organization/src/main/java/dev/vericov/organization/application/port/OrganizationRepository.java`
- `services/organization/src/main/java/dev/vericov/organization/application/InMemoryOrganizationRepository.java`
- `services/organization/src/main/java/dev/vericov/organization/adapter/jdbc/JdbcOrganizationRepository.java`
- `services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java`
- `services/organization/src/test/java/dev/vericov/organization/api/OrganizationResourceTest.java`
- `services/organization/src/test/java/dev/vericov/organization/api/OrganizationResourceIntegrationTest.java`
- `services/organization/src/test/java/dev/vericov/organization/bdd/steps/OrganizationSteps.java`
- `services/organization/src/test/resources/features/organization/organization-management.feature`
- `infra/supabase/volumes/db/vericov.sql`
- `docs/backend/services/02-api-control-plane-service.md`
- `infra/kong/README.md`
- `docs/backend/services/01-kong-api-gateway.md`

---

### Task 1: Add Application Records and Config Copy Helpers

**Files:**
- Create: `services/organization/src/main/java/dev/vericov/organization/application/ConfigurationValues.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/PolicyDefaultsDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/RepositoryConfigDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/RepositoryPolicyDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/RepositoryGateDetails.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/application/EffectiveRepositoryConfig.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java`

- [ ] **Step 1: Add failing immutability tests**

Append these tests to `OrganizationApplicationServiceTest`:

```java
@Test
void repositoryConfigDetailsDefensivelyCopiesNestedConfig() {
    Map<String, Object> nested = new java.util.HashMap<>();
    nested.put("target", 80);
    Map<String, Object> config = new java.util.HashMap<>();
    config.put("coverage", nested);

    RepositoryConfigDetails details = new RepositoryConfigDetails(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ui_override",
            config,
            1,
            "valid",
            List.of(),
            USER_ID,
            NOW,
            NOW);

    nested.put("target", 50);

    @SuppressWarnings("unchecked")
    Map<String, Object> storedCoverage = (Map<String, Object>) details.config().get("coverage");
    assertEquals(80, storedCoverage.get("target"));
}
```

Expected compile failure: `RepositoryConfigDetails` does not exist.

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest#repositoryConfigDetailsDefensivelyCopiesNestedConfig test
```

Expected: FAIL at compile because config records do not exist.

- [ ] **Step 3: Add `ConfigurationValues`**

Create `ConfigurationValues.java` with:

```java
package dev.vericov.organization.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConfigurationValues {
    private ConfigurationValues() {
    }

    static Map<String, Object> deepCopyMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, entryValue) -> copy.put(key, deepCopyValue(entryValue)));
        return Map.copyOf(copy);
    }

    static List<Object> deepCopyList(List<?> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<Object> copy = new ArrayList<>();
        value.forEach(entry -> copy.add(deepCopyValue(entry)));
        return List.copyOf(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> copy = new LinkedHashMap<>();
            mapValue.forEach((key, entryValue) -> {
                if (!(key instanceof String stringKey)) {
                    throw new OrganizationException("validation_error", "config key must be a string");
                }
                copy.put(stringKey, deepCopyValue(entryValue));
            });
            return Map.copyOf(copy);
        }
        if (value instanceof List<?> listValue) {
            return deepCopyList(listValue);
        }
        return value;
    }
}
```

- [ ] **Step 4: Add immutable detail records**

Create `RepositoryConfigDetails.java` with:

```java
package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RepositoryConfigDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String source,
        Map<String, Object> config,
        int schemaVersion,
        String validationStatus,
        List<String> validationErrors,
        UUID updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryConfigDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(source, "source");
        config = ConfigurationValues.deepCopyMap(config);
        validationErrors = List.copyOf(validationErrors == null ? List.of() : validationErrors);
        Objects.requireNonNull(validationStatus, "validationStatus");
        Objects.requireNonNull(updatedByUserId, "updatedByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RepositoryConfigDetails withValues(
            Map<String, Object> nextConfig,
            int nextSchemaVersion,
            String nextValidationStatus,
            List<String> nextValidationErrors,
            UUID nextUpdatedByUserId,
            Instant nextUpdatedAt) {
        return new RepositoryConfigDetails(
                id,
                tenantId,
                organizationId,
                repositoryId,
                source,
                nextConfig,
                nextSchemaVersion,
                nextValidationStatus,
                nextValidationErrors,
                nextUpdatedByUserId,
                createdAt,
                nextUpdatedAt);
    }
}
```

Create the other records using the same defensive-copy pattern:

```java
public record PolicyDefaultsDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        Map<String, Object> defaults,
        int schemaVersion,
        UUID updatedByUserId,
        Instant createdAt,
        Instant updatedAt) { }
```

```java
public record RepositoryPolicyDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String description,
        String policyType,
        String targetType,
        String targetSelector,
        Map<String, Object> config,
        String status,
        int priority,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) { }
```

```java
public record RepositoryGateDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String gateType,
        String metric,
        java.math.BigDecimal threshold,
        java.math.BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status,
        Instant createdAt,
        Instant updatedAt) { }
```

```java
public record EffectiveRepositoryConfig(
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        Map<String, Object> orgDefaults,
        Map<String, Object> repositoryConfig,
        List<RepositoryPolicyDetails> policies,
        List<RepositoryGateDetails> gates,
        Instant resolvedAt) { }
```

In each compact constructor, call `Objects.requireNonNull` for required fields, `ConfigurationValues.deepCopyMap` for maps, and `List.copyOf` for lists.

- [ ] **Step 5: Run the immutability test**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest#repositoryConfigDetailsDefensivelyCopiesNestedConfig test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/organization/src/main/java/dev/vericov/organization/application services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java
git commit -m "feat: add repository policy config records"
```

### Task 2: Add Repository Port Methods and In-Memory Storage

**Files:**
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/port/OrganizationRepository.java`
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/InMemoryOrganizationRepository.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java`

- [ ] **Step 1: Add failing repository adapter tests**

Append this test:

```java
@Test
void inMemoryRepositoryStoresConfigPoliciesAndGatesPerRepository() {
    TestFixture fixture = new TestFixture();
    var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
            USER_ID,
            "Acme Engineering",
            "acme",
            "team"));
    var repository = fixture.service.registerRepository(new CreateRepositoryCommand(
            USER_ID,
            organization.id(),
            "github",
            "123456789",
            "acme/payments-api",
            "main",
            "private"));

    RepositoryConfigDetails config = new RepositoryConfigDetails(
            UUID.randomUUID(),
            organization.tenantId(),
            organization.id(),
            repository.id(),
            "ui_override",
            Map.of("coverage", Map.of("project", Map.of("target", 82))),
            1,
            "valid",
            List.of(),
            USER_ID,
            NOW,
            NOW);

    fixture.repository.saveRepositoryConfig(config);

    assertEquals(config.id(), fixture.repository.findRepositoryConfig(organization.id(), repository.id()).orElseThrow().id());
}
```

Expected compile failure: repository methods do not exist.

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest#inMemoryRepositoryStoresConfigPoliciesAndGatesPerRepository test
```

Expected: FAIL at compile.

- [ ] **Step 3: Extend `OrganizationRepository`**

Add these methods:

```java
Optional<PolicyDefaultsDetails> findPolicyDefaults(UUID organizationId);

PolicyDefaultsDetails savePolicyDefaults(PolicyDefaultsDetails defaults);

PolicyDefaultsDetails updatePolicyDefaults(PolicyDefaultsDetails defaults);

Optional<RepositoryConfigDetails> findRepositoryConfig(UUID organizationId, UUID repositoryId);

RepositoryConfigDetails saveRepositoryConfig(RepositoryConfigDetails config);

RepositoryConfigDetails updateRepositoryConfig(RepositoryConfigDetails config);

List<RepositoryPolicyDetails> listRepositoryPolicies(UUID organizationId, UUID repositoryId);

Optional<RepositoryPolicyDetails> findRepositoryPolicy(UUID organizationId, UUID repositoryId, UUID policyId);

RepositoryPolicyDetails saveRepositoryPolicy(RepositoryPolicyDetails policy);

RepositoryPolicyDetails updateRepositoryPolicy(RepositoryPolicyDetails policy);

List<RepositoryGateDetails> listRepositoryGates(UUID organizationId, UUID repositoryId);

void replaceRepositoryGates(UUID organizationId, UUID repositoryId, List<RepositoryGateDetails> gates);
```

- [ ] **Step 4: Implement in-memory maps**

Add maps:

```java
private final Map<UUID, PolicyDefaultsDetails> policyDefaultsByOrgId = new ConcurrentHashMap<>();
private final Map<String, RepositoryConfigDetails> repositoryConfigsByOrgAndRepo = new ConcurrentHashMap<>();
private final Map<UUID, RepositoryPolicyDetails> repositoryPoliciesById = new ConcurrentHashMap<>();
private final Map<UUID, RepositoryGateDetails> repositoryGatesById = new ConcurrentHashMap<>();
```

Add helper:

```java
private static String orgRepositoryKey(UUID organizationId, UUID repositoryId) {
    return organizationId + ":" + repositoryId;
}
```

Implement list methods sorted by `priority`, `name`, then `id` for policies and by `name`, then `id` for gates.

- [ ] **Step 5: Run the adapter test**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest#inMemoryRepositoryStoresConfigPoliciesAndGatesPerRepository test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/organization/src/main/java/dev/vericov/organization/application/port/OrganizationRepository.java services/organization/src/main/java/dev/vericov/organization/application/InMemoryOrganizationRepository.java services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java
git commit -m "feat: store repository policy config in memory"
```

### Task 3: Add Application Commands and Validation

**Files:**
- Create command records listed in File Structure
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/OrganizationApplicationService.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java`

- [ ] **Step 1: Add failing service tests for org defaults and repository config**

Add tests:

```java
@Test
void ownerUpdatesPolicyDefaultsAndRepositoryConfig() {
    TestFixture fixture = new TestFixture();
    var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
            USER_ID, "Acme Engineering", "acme", "team"));
    var repository = fixture.service.registerRepository(new CreateRepositoryCommand(
            USER_ID, organization.id(), "github", "123456789", "acme/payments-api", "main", "private"));

    PolicyDefaultsDetails defaults = fixture.service.upsertPolicyDefaults(new UpsertPolicyDefaultsCommand(
            USER_ID,
            organization.id(),
            Map.of("coverage", Map.of("project", Map.of("target", 80))),
            1));

    RepositoryConfigDetails config = fixture.service.upsertRepositoryConfig(new UpsertRepositoryConfigCommand(
            USER_ID,
            organization.id(),
            repository.id(),
            Map.of("coverage", Map.of("patch", Map.of("target", 85))),
            1));

    assertEquals(organization.id(), defaults.organizationId());
    assertEquals("ui_override", config.source());
    assertEquals("valid", config.validationStatus());
}

@Test
void viewerCannotMutatePolicyDefaultsOrRepositoryConfig() {
    TestFixture fixture = new TestFixture();
    var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
            USER_ID, "Acme Engineering", "acme", "team"));
    var repository = fixture.service.registerRepository(new CreateRepositoryCommand(
            USER_ID, organization.id(), "github", "123456789", "acme/payments-api", "main", "private"));
    fixture.service.addMembership(new CreateMembershipCommand(
            USER_ID, organization.id(), OTHER_USER_ID, "viewer", "active"));

    OrganizationException exception = assertThrows(
            OrganizationException.class,
            () -> fixture.service.upsertRepositoryConfig(new UpsertRepositoryConfigCommand(
                    OTHER_USER_ID,
                    organization.id(),
                    repository.id(),
                    Map.of("coverage", Map.of("patch", Map.of("target", 85))),
                    1)));

    assertEquals("forbidden", exception.code());
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest#ownerUpdatesPolicyDefaultsAndRepositoryConfig,OrganizationApplicationServiceTest#viewerCannotMutatePolicyDefaultsOrRepositoryConfig test
```

Expected: FAIL at compile because commands and methods do not exist.

- [ ] **Step 3: Add command records**

Create:

```java
public record UpsertPolicyDefaultsCommand(
        UUID requesterUserId,
        UUID organizationId,
        Map<String, Object> defaults,
        int schemaVersion) { }
```

```java
public record UpsertRepositoryConfigCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        Map<String, Object> config,
        int schemaVersion) { }
```

```java
public record ValidateRepositoryConfigCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        Map<String, Object> config,
        int schemaVersion) { }
```

```java
public record CreateRepositoryPolicyCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String description,
        String policyType,
        String targetType,
        String targetSelector,
        Map<String, Object> config,
        String status,
        int priority) { }
```

```java
public record UpdateRepositoryPolicyCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        UUID policyId,
        String name,
        String description,
        String policyType,
        String targetType,
        String targetSelector,
        Map<String, Object> config,
        String status,
        Integer priority) { }
```

```java
public record UpsertRepositoryGatesCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        List<RepositoryGateDetails> gates) { }
```

- [ ] **Step 4: Add validation helpers to `OrganizationApplicationService`**

Add sets:

```java
private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");
private static final Set<String> POLICY_TYPES = Set.of("coverage", "mutation", "agent_review", "waiver");
private static final Set<String> POLICY_TARGET_TYPES = Set.of("repository", "component", "path");
private static final Set<String> POLICY_STATUSES = Set.of("active", "disabled");
private static final Set<String> GATE_TYPES = Set.of(
        "project_coverage",
        "patch_coverage",
        "coverage_drop",
        "component_coverage",
        "mutation_score",
        "agent_review_required");
private static final Set<String> GATE_METRICS = Set.of("line", "branch", "function", "statement", "mutation", "risk");
```

Add methods:

```java
private static Map<String, Object> validateConfig(Map<String, Object> config) {
    Map<String, Object> copy = ConfigurationValues.deepCopyMap(config);
    validateConfigKeys(copy);
    return copy;
}

private static void validateConfigKeys(Map<?, ?> config) {
    for (Map.Entry<?, ?> entry : config.entrySet()) {
        if (!(entry.getKey() instanceof String key)
                || key.isBlank()
                || !CONFIG_KEY_PATTERN.matcher(key).matches()) {
            throw new OrganizationException("validation_error", "config key is invalid");
        }
        if (entry.getValue() instanceof Map<?, ?> nestedMap) {
            validateConfigKeys(nestedMap);
        }
    }
}

private static int validateSchemaVersion(int schemaVersion) {
    if (schemaVersion < 1) {
        throw new OrganizationException("validation_error", "schema_version must be positive");
    }
    return schemaVersion;
}
```

- [ ] **Step 5: Add service methods**

Implement:

```java
public PolicyDefaultsDetails upsertPolicyDefaults(UpsertPolicyDefaultsCommand command)
public PolicyDefaultsDetails getPolicyDefaults(UUID requesterUserId, UUID organizationId)
public RepositoryConfigDetails upsertRepositoryConfig(UpsertRepositoryConfigCommand command)
public RepositoryConfigDetails validateRepositoryConfig(ValidateRepositoryConfigCommand command)
public EffectiveRepositoryConfig getEffectiveRepositoryConfig(UUID requesterUserId, UUID organizationId, UUID repositoryId)
```

Each method must:

- Require authenticated user.
- Require active membership for read.
- Require admin for write.
- Call `requireOrganization(organizationId)`.
- Call `getRepository(requesterUserId, organizationId, repositoryId)` for repository scoped methods.
- Use existing config if updating, otherwise create a new record.
- Store `validationStatus = "valid"` and `validationErrors = List.of()` for valid config.

- [ ] **Step 6: Run service tests**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/organization/src/main/java/dev/vericov/organization/application services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java
git commit -m "feat: manage repository config settings"
```

### Task 4: Add Repository Policies and Gates in the Application Service

**Files:**
- Modify: `services/organization/src/main/java/dev/vericov/organization/application/OrganizationApplicationService.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java`

- [ ] **Step 1: Add failing policy and gate tests**

Add:

```java
@Test
void ownerCreatesPolicyAndReplacesGates() {
    TestFixture fixture = new TestFixture();
    var organization = fixture.service.createOrganization(new CreateOrganizationCommand(
            USER_ID, "Acme Engineering", "acme", "team"));
    var repository = fixture.service.registerRepository(new CreateRepositoryCommand(
            USER_ID, organization.id(), "github", "123456789", "acme/payments-api", "main", "private"));

    RepositoryPolicyDetails policy = fixture.service.createRepositoryPolicy(new CreateRepositoryPolicyCommand(
            USER_ID,
            organization.id(),
            repository.id(),
            "Patch coverage policy",
            "Require patch coverage for changed lines",
            "coverage",
            "repository",
            null,
            Map.of("metric", "line", "target", 85),
            "active",
            100));

    RepositoryGateDetails gate = new RepositoryGateDetails(
            UUID.randomUUID(),
            organization.tenantId(),
            organization.id(),
            repository.id(),
            "Patch line coverage",
            "patch_coverage",
            "line",
            new java.math.BigDecimal("85.0"),
            null,
            true,
            Map.of(),
            "active",
            NOW,
            NOW);

    List<RepositoryGateDetails> gates = fixture.service.replaceRepositoryGates(new UpsertRepositoryGatesCommand(
            USER_ID,
            organization.id(),
            repository.id(),
            List.of(gate)));

    assertEquals("coverage", policy.policyType());
    assertEquals(1, gates.size());
    assertEquals("patch_coverage", gates.getFirst().gateType());
}
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest#ownerCreatesPolicyAndReplacesGates test
```

Expected: FAIL at compile.

- [ ] **Step 3: Implement policy methods**

Add:

```java
public List<RepositoryPolicyDetails> listRepositoryPolicies(UUID requesterUserId, UUID organizationId, UUID repositoryId)
public RepositoryPolicyDetails createRepositoryPolicy(CreateRepositoryPolicyCommand command)
public RepositoryPolicyDetails updateRepositoryPolicy(UpdateRepositoryPolicyCommand command)
```

Validation:

- `name` uses existing 1-120 character name validation logic but with message `"policy name must be 1 to 120 characters"`.
- `policyType` uses `POLICY_TYPES`.
- `targetType` uses `POLICY_TARGET_TYPES`.
- `targetSelector` required for `component` and `path`.
- `priority` range 0-1000.

- [ ] **Step 4: Implement gate methods**

Add:

```java
public List<RepositoryGateDetails> listRepositoryGates(UUID requesterUserId, UUID organizationId, UUID repositoryId)
public List<RepositoryGateDetails> replaceRepositoryGates(UpsertRepositoryGatesCommand command)
public List<RepositoryGateDetails> validateRepositoryGates(UpsertRepositoryGatesCommand command)
```

Validation:

- Gate belongs to same tenant/org/repository as command target.
- Gate type is in `GATE_TYPES`.
- Metric is in `GATE_METRICS`.
- Threshold is required unless `gateType` is `agent_review_required`.
- `maxDrop` is allowed only for `coverage_drop`.
- Threshold and max drop must be >= 0.

- [ ] **Step 5: Run policy/gate tests**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/organization/src/main/java/dev/vericov/organization/application services/organization/src/test/java/dev/vericov/organization/application/OrganizationApplicationServiceTest.java
git commit -m "feat: manage repository policies and gates"
```

### Task 5: Add Public API Request/Response Records

**Files:**
- Create API request/response records listed in File Structure
- Test: `services/organization/src/test/java/dev/vericov/organization/api/OrganizationResourceTest.java`

- [ ] **Step 1: Add failing API record test**

Add a resource test that constructs a `RepositoryConfigHttpRequest`:

```java
@Test
void repositoryConfigRequestMapsToCommand() {
    UUID orgId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    RepositoryConfigHttpRequest request = new RepositoryConfigHttpRequest(
            Map.of("coverage", Map.of("patch", Map.of("target", 85))),
            1);

    UpsertRepositoryConfigCommand command = request.toCommand(USER_ID, orgId, repoId);

    assertEquals(USER_ID, command.requesterUserId());
    assertEquals(orgId, command.organizationId());
    assertEquals(repoId, command.repositoryId());
    assertEquals(1, command.schemaVersion());
}
```

Expected compile failure: API records do not exist.

- [ ] **Step 2: Run failing test**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationResourceTest#repositoryConfigRequestMapsToCommand test
```

Expected: FAIL at compile.

- [ ] **Step 3: Add API records**

Use JSON-B snake_case where needed:

```java
public record RepositoryConfigHttpRequest(
        Map<String, Object> config,
        @JsonbProperty("schema_version")
        int schemaVersion) {

    public UpsertRepositoryConfigCommand toCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new UpsertRepositoryConfigCommand(requesterUserId, organizationId, repositoryId, config, schemaVersion);
    }
}
```

```java
public record RepositoryConfigHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id") UUID tenantId,
        @JsonbProperty("org_id") UUID organizationId,
        @JsonbProperty("repository_id") UUID repositoryId,
        String source,
        Map<String, Object> config,
        @JsonbProperty("schema_version") int schemaVersion,
        @JsonbProperty("validation_status") String validationStatus,
        @JsonbProperty("validation_errors") List<String> validationErrors,
        @JsonbProperty("updated_by_user_id") UUID updatedByUserId,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("updated_at") Instant updatedAt) {

    public static RepositoryConfigHttpResponse from(RepositoryConfigDetails details) {
        return new RepositoryConfigHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.source(),
                details.config(),
                details.schemaVersion(),
                details.validationStatus(),
                details.validationErrors(),
                details.updatedByUserId(),
                details.createdAt(),
                details.updatedAt());
    }
}
```

Add these additional API records with the listed fields and mapper methods:

```java
public record PolicyDefaultsHttpRequest(
        Map<String, Object> defaults,
        @JsonbProperty("schema_version") int schemaVersion) {

    public UpsertPolicyDefaultsCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new UpsertPolicyDefaultsCommand(requesterUserId, organizationId, defaults, schemaVersion);
    }
}
```

```java
public record PolicyDefaultsHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id") UUID tenantId,
        @JsonbProperty("org_id") UUID organizationId,
        Map<String, Object> defaults,
        @JsonbProperty("schema_version") int schemaVersion,
        @JsonbProperty("updated_by_user_id") UUID updatedByUserId,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("updated_at") Instant updatedAt) {

    public static PolicyDefaultsHttpResponse from(PolicyDefaultsDetails details) {
        return new PolicyDefaultsHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.defaults(),
                details.schemaVersion(),
                details.updatedByUserId(),
                details.createdAt(),
                details.updatedAt());
    }
}
```

```java
public record RepositoryPolicyHttpRequest(
        String name,
        String description,
        @JsonbProperty("policy_type") String policyType,
        @JsonbProperty("target_type") String targetType,
        @JsonbProperty("target_selector") String targetSelector,
        Map<String, Object> config,
        String status,
        int priority) {

    public CreateRepositoryPolicyCommand toCreateCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new CreateRepositoryPolicyCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                name,
                description,
                policyType,
                targetType,
                targetSelector,
                config,
                status,
                priority);
    }

    public UpdateRepositoryPolicyCommand toUpdateCommand(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId,
            UUID policyId) {
        return new UpdateRepositoryPolicyCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                policyId,
                name,
                description,
                policyType,
                targetType,
                targetSelector,
                config,
                status,
                priority);
    }
}
```

```java
public record RepositoryPolicyHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id") UUID tenantId,
        @JsonbProperty("org_id") UUID organizationId,
        @JsonbProperty("repository_id") UUID repositoryId,
        String name,
        String description,
        @JsonbProperty("policy_type") String policyType,
        @JsonbProperty("target_type") String targetType,
        @JsonbProperty("target_selector") String targetSelector,
        Map<String, Object> config,
        String status,
        int priority,
        @JsonbProperty("created_by_user_id") UUID createdByUserId,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("updated_at") Instant updatedAt) {

    public static RepositoryPolicyHttpResponse from(RepositoryPolicyDetails details) {
        return new RepositoryPolicyHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.name(),
                details.description(),
                details.policyType(),
                details.targetType(),
                details.targetSelector(),
                details.config(),
                details.status(),
                details.priority(),
                details.createdByUserId(),
                details.createdAt(),
                details.updatedAt());
    }
}
```

```java
public record RepositoryGateHttpRequest(
        String name,
        @JsonbProperty("gate_type") String gateType,
        String metric,
        BigDecimal threshold,
        @JsonbProperty("max_drop") BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status) {

    public RepositoryGateDetails toDetails(
            UUID tenantId,
            UUID organizationId,
            UUID repositoryId,
            Instant now) {
        return new RepositoryGateDetails(
                UUID.randomUUID(),
                tenantId,
                organizationId,
                repositoryId,
                name,
                gateType,
                metric,
                threshold,
                maxDrop,
                blocking,
                config,
                status,
                now,
                now);
    }
}
```

```java
public record RepositoryGateHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id") UUID tenantId,
        @JsonbProperty("org_id") UUID organizationId,
        @JsonbProperty("repository_id") UUID repositoryId,
        String name,
        @JsonbProperty("gate_type") String gateType,
        String metric,
        BigDecimal threshold,
        @JsonbProperty("max_drop") BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("updated_at") Instant updatedAt) {

    public static RepositoryGateHttpResponse from(RepositoryGateDetails details) {
        return new RepositoryGateHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.name(),
                details.gateType(),
                details.metric(),
                details.threshold(),
                details.maxDrop(),
                details.blocking(),
                details.config(),
                details.status(),
                details.createdAt(),
                details.updatedAt());
    }
}
```

```java
public record RepositoryConfigValidationHttpResponse(
        @JsonbProperty("validation_status") String validationStatus,
        @JsonbProperty("validation_errors") List<String> validationErrors,
        Map<String, Object> config,
        @JsonbProperty("schema_version") int schemaVersion) {

    public static RepositoryConfigValidationHttpResponse from(RepositoryConfigDetails details) {
        return new RepositoryConfigValidationHttpResponse(
                details.validationStatus(),
                details.validationErrors(),
                details.config(),
                details.schemaVersion());
    }
}
```

```java
public record EffectiveRepositoryConfigHttpResponse(
        @JsonbProperty("tenant_id") UUID tenantId,
        @JsonbProperty("org_id") UUID organizationId,
        @JsonbProperty("repository_id") UUID repositoryId,
        @JsonbProperty("org_defaults") Map<String, Object> orgDefaults,
        @JsonbProperty("repository_config") Map<String, Object> repositoryConfig,
        List<RepositoryPolicyHttpResponse> policies,
        List<RepositoryGateHttpResponse> gates,
        @JsonbProperty("resolved_at") Instant resolvedAt) {

    public static EffectiveRepositoryConfigHttpResponse from(EffectiveRepositoryConfig config) {
        return new EffectiveRepositoryConfigHttpResponse(
                config.tenantId(),
                config.organizationId(),
                config.repositoryId(),
                config.orgDefaults(),
                config.repositoryConfig(),
                config.policies().stream().map(RepositoryPolicyHttpResponse::from).toList(),
                config.gates().stream().map(RepositoryGateHttpResponse::from).toList(),
                config.resolvedAt());
    }
}
```

- [ ] **Step 4: Run API record test**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationResourceTest#repositoryConfigRequestMapsToCommand test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/organization/src/main/java/dev/vericov/organization/api services/organization/src/test/java/dev/vericov/organization/api/OrganizationResourceTest.java
git commit -m "feat: add repository config API models"
```

### Task 6: Add Public and Internal Resources

**Files:**
- Create: `services/organization/src/main/java/dev/vericov/organization/api/RepositoryControlPlaneResource.java`
- Create: `services/organization/src/main/java/dev/vericov/organization/api/InternalRepositoryConfigResource.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/api/OrganizationResourceTest.java`
- Test: `services/organization/src/test/java/dev/vericov/organization/api/OrganizationResourceIntegrationTest.java`

- [ ] **Step 1: Add failing resource tests**

Add:

```java
@Test
void upsertsRepositoryConfigThroughOrgScopedRoute() {
    OrganizationApplicationService service = service();
    var organizationResource = resourceWithUser(service, USER_ID, "owner@example.com");
    var configResource = new RepositoryControlPlaneResource(service, fixedUser(USER_ID, "owner@example.com"));
    OrganizationHttpResponse organization = createOrganization(organizationResource);
    RepositoryHttpResponse repository = registerRepository(organizationResource, organization.id());

    Response response = configResource.upsertRepositoryConfig(
            "Bearer test-token",
            null,
            organization.id(),
            repository.id(),
            new RepositoryConfigHttpRequest(
                    Map.of("coverage", Map.of("patch", Map.of("target", 85))),
                    1));

    assertEquals(200, response.getStatus());
    RepositoryConfigHttpResponse body = responseBody(response, RepositoryConfigHttpResponse.class);
    assertEquals(repository.id(), body.repositoryId());
    assertEquals("valid", body.validationStatus());
}
```

Expected compile failure: resource does not exist.

- [ ] **Step 2: Run failing resource test**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationResourceTest#upsertsRepositoryConfigThroughOrgScopedRoute test
```

Expected: FAIL at compile.

- [ ] **Step 3: Implement `RepositoryControlPlaneResource`**

Create a JAX-RS resource with this class shape and exact route methods. Each method resolves the user, calls the matching service method, wraps success in `ApiResponse`, and maps `OrganizationException` through `OrganizationResource.errorResponse`.

```java
@ApplicationScoped
@Path("/api/v1/orgs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RepositoryControlPlaneResource {
    private final OrganizationApplicationService organizationService;
    private final UserPrincipalResolver userPrincipalResolver;

    @Inject
    public RepositoryControlPlaneResource(
            OrganizationApplicationService organizationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.organizationService = organizationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GET
    @Path("/{org_id}/policy-defaults")
    public Response getPolicyDefaults(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var defaults = organizationService.getPolicyDefaults(user.userId(), organizationId);
            return Response.ok(new ApiResponse<>(PolicyDefaultsHttpResponse.from(defaults))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PUT
    @Path("/{org_id}/policy-defaults")
    public Response upsertPolicyDefaults(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            PolicyDefaultsHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var defaults = organizationService.upsertPolicyDefaults(request.toCommand(user.userId(), organizationId));
            return Response.ok(new ApiResponse<>(PolicyDefaultsHttpResponse.from(defaults))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/config")
    public Response getEffectiveRepositoryConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.getEffectiveRepositoryConfig(user.userId(), organizationId, repositoryId);
            return Response.ok(new ApiResponse<>(EffectiveRepositoryConfigHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PUT
    @Path("/{org_id}/repositories/{repository_id}/config")
    public Response upsertRepositoryConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            RepositoryConfigHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.upsertRepositoryConfig(
                    request.toCommand(user.userId(), organizationId, repositoryId));
            return Response.ok(new ApiResponse<>(RepositoryConfigHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories/{repository_id}/config/validate")
    public Response validateRepositoryConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            RepositoryConfigHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.validateRepositoryConfig(new ValidateRepositoryConfigCommand(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    request.config(),
                    request.schemaVersion()));
            return Response.ok(new ApiResponse<>(RepositoryConfigValidationHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/policies")
    public Response listRepositoryPolicies(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var policies = organizationService.listRepositoryPolicies(user.userId(), organizationId, repositoryId)
                    .stream()
                    .map(RepositoryPolicyHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(policies)).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories/{repository_id}/policies")
    public Response createRepositoryPolicy(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            RepositoryPolicyHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var policy = organizationService.createRepositoryPolicy(
                    request.toCreateCommand(user.userId(), organizationId, repositoryId));
            return Response.created(URI.create("/api/v1/orgs/" + organizationId
                            + "/repositories/" + repositoryId
                            + "/policies/" + policy.id()))
                    .entity(new ApiResponse<>(RepositoryPolicyHttpResponse.from(policy)))
                    .build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PATCH
    @Path("/{org_id}/repositories/{repository_id}/policies/{policy_id}")
    public Response updateRepositoryPolicy(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            @PathParam("policy_id") UUID policyId,
            RepositoryPolicyHttpRequest request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var policy = organizationService.updateRepositoryPolicy(
                    request.toUpdateCommand(user.userId(), organizationId, repositoryId, policyId));
            return Response.ok(new ApiResponse<>(RepositoryPolicyHttpResponse.from(policy))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/gates")
    public Response listRepositoryGates(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var gates = organizationService.listRepositoryGates(user.userId(), organizationId, repositoryId)
                    .stream()
                    .map(RepositoryGateHttpResponse::from)
                    .toList();
            return Response.ok(new ApiResponse<>(gates)).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @PUT
    @Path("/{org_id}/repositories/{repository_id}/gates")
    public Response replaceRepositoryGates(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            List<RepositoryGateHttpRequest> request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repository = organizationService.getRepository(user.userId(), organizationId, repositoryId);
            Instant now = Instant.now();
            var gates = organizationService.replaceRepositoryGates(new UpsertRepositoryGatesCommand(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    request.stream()
                            .map(gate -> gate.toDetails(repository.tenantId(), organizationId, repositoryId, now))
                            .toList()));
            return Response.ok(new ApiResponse<>(gates.stream().map(RepositoryGateHttpResponse::from).toList())).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    @POST
    @Path("/{org_id}/repositories/{repository_id}/gates/validate")
    public Response validateRepositoryGates(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId,
            List<RepositoryGateHttpRequest> request) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var repository = organizationService.getRepository(user.userId(), organizationId, repositoryId);
            Instant now = Instant.now();
            var gates = organizationService.validateRepositoryGates(new UpsertRepositoryGatesCommand(
                    user.userId(),
                    organizationId,
                    repositoryId,
                    request.stream()
                            .map(gate -> gate.toDetails(repository.tenantId(), organizationId, repositoryId, now))
                            .toList()));
            return Response.ok(new ApiResponse<>(gates.stream().map(RepositoryGateHttpResponse::from).toList())).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    private AuthenticatedUser resolveUser(String authorizationHeader, String userIdHeader) {
        return userPrincipalResolver.resolve(new UserAuthContext(authorizationHeader, userIdHeader));
    }
}
```

- [ ] **Step 4: Implement internal effective-config resource**

Create:

```java
@ApplicationScoped
@Path("/internal/v1/orgs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalRepositoryConfigResource {
    private final OrganizationApplicationService organizationService;
    private final UserPrincipalResolver userPrincipalResolver;

    @Inject
    public InternalRepositoryConfigResource(
            OrganizationApplicationService organizationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.organizationService = organizationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GET
    @Path("/{org_id}/repositories/{repository_id}/effective-config")
    public Response getEffectiveConfig(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam("X-Vericov-User-Id") String userIdHeader,
            @PathParam("org_id") UUID organizationId,
            @PathParam("repository_id") UUID repositoryId) {
        try {
            AuthenticatedUser user = resolveUser(authorizationHeader, userIdHeader);
            var config = organizationService.getEffectiveRepositoryConfig(
                    user.userId(),
                    organizationId,
                    repositoryId);
            return Response.ok(new ApiResponse<>(EffectiveRepositoryConfigHttpResponse.from(config))).build();
        } catch (OrganizationException exception) {
            return OrganizationResource.errorResponse(exception);
        }
    }

    private AuthenticatedUser resolveUser(String authorizationHeader, String userIdHeader) {
        return userPrincipalResolver.resolve(new UserAuthContext(authorizationHeader, userIdHeader));
    }
}
```

- [ ] **Step 5: Run resource tests**

Run:

```bash
mvn -pl services/organization -Dtest=OrganizationResourceTest,OrganizationResourceIntegrationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/organization/src/main/java/dev/vericov/organization/api services/organization/src/test/java/dev/vericov/organization/api
git commit -m "feat: expose repository policy config APIs"
```

### Task 7: Add Supabase Schema and JDBC Persistence

**Files:**
- Modify: `infra/supabase/volumes/db/vericov.sql`
- Modify: `services/organization/src/main/java/dev/vericov/organization/adapter/jdbc/JdbcOrganizationRepository.java`
- Test: existing service tests with in-memory

- [ ] **Step 1: Add SQL tables**

Add after `vericov.repositories`:

```sql
CREATE TABLE IF NOT EXISTS vericov.organization_policy_defaults (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL UNIQUE REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    defaults_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    updated_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.repository_configs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    source text NOT NULL CHECK (source IN ('ui_override', 'repo_file', 'effective_snapshot')),
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    validation_status text NOT NULL DEFAULT 'valid'
        CHECK (validation_status IN ('valid', 'invalid', 'warning')),
    validation_errors jsonb NOT NULL DEFAULT '[]'::jsonb,
    updated_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (repository_id, source)
);

CREATE TABLE IF NOT EXISTS vericov.repository_policies (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    description text,
    policy_type text NOT NULL CHECK (policy_type IN ('coverage', 'mutation', 'agent_review', 'waiver')),
    target_type text NOT NULL CHECK (target_type IN ('repository', 'component', 'path')),
    target_selector text,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    priority integer NOT NULL DEFAULT 100 CHECK (priority BETWEEN 0 AND 1000),
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.repository_gate_configurations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    gate_type text NOT NULL,
    metric text NOT NULL,
    threshold numeric,
    max_drop numeric,
    blocking boolean NOT NULL DEFAULT true,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (repository_id, name)
);
```

- [ ] **Step 2: Add indexes and RLS**

Add:

```sql
CREATE INDEX IF NOT EXISTS repository_configs_repository_source_idx
    ON vericov.repository_configs (repository_id, source);

CREATE INDEX IF NOT EXISTS repository_policies_repository_status_priority_idx
    ON vericov.repository_policies (repository_id, status, priority);

CREATE INDEX IF NOT EXISTS repository_gate_configurations_repository_status_idx
    ON vericov.repository_gate_configurations (repository_id, status, name);

ALTER TABLE vericov.organization_policy_defaults ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.repository_gate_configurations ENABLE ROW LEVEL SECURITY;
```

Update the revoke block so all tables remain private.

- [ ] **Step 3: Implement JDBC methods**

In `JdbcOrganizationRepository`, add SQL methods matching the repository port. Store maps/lists as JSONB strings using JSON-P:

```java
private static String jsonObject(Map<String, Object> value) {
    return value == null || value.isEmpty() ? "{}" : Json.createObjectBuilder(value).build().toString();
}
```

If JSON-P object-builder cannot accept arbitrary nested maps cleanly, add a small recursive JSON converter in this file rather than using string concatenation.

- [ ] **Step 4: Run organization tests**

Run:

```bash
mvn -pl services/organization test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add infra/supabase/volumes/db/vericov.sql services/organization/src/main/java/dev/vericov/organization/adapter/jdbc/JdbcOrganizationRepository.java
git commit -m "feat: persist repository policy config"
```

### Task 8: Add BDD Coverage and Contract Docs

**Files:**
- Modify: `services/organization/src/test/resources/features/organization/organization-management.feature`
- Modify: `services/organization/src/test/java/dev/vericov/organization/bdd/steps/OrganizationSteps.java`
- Modify: `docs/backend/services/02-api-control-plane-service.md`
- Modify: `docs/backend/services/01-kong-api-gateway.md`
- Modify: `infra/kong/README.md`

- [ ] **Step 1: Add BDD scenarios**

Append:

```gherkin
Scenario: Owners configure repository coverage gates
  Given authenticated user "owner@example.com"
  And the current user created organization "Acme Engineering" with slug "gate-config-flow"
  And the current user registered GitHub repository "acme/payments-api"
  When the current user configures a blocking patch line coverage gate at 85 percent
  Then the repository gate API stores the blocking patch line coverage gate

Scenario: Viewers cannot change repository config
  Given authenticated user "owner@example.com"
  And the current user created organization "Acme Engineering" with slug "config-authz-flow"
  And the current user registered GitHub repository "acme/payments-api"
  And the current user adds user "viewer@example.com" as "viewer"
  When user "viewer@example.com" updates repository config
  Then the organization API rejects the request with status 403 and code "forbidden"
```

- [ ] **Step 2: Add step definitions**

In `OrganizationSteps`, add fields:

```java
private RepositoryHttpResponse repository;
private RepositoryGateHttpResponse gate;
```

Add steps that call `RepositoryControlPlaneResource` with `RepositoryGateHttpRequest`.

- [ ] **Step 3: Update contract docs**

In `docs/backend/services/02-api-control-plane-service.md`:

- Mark repository config, policies, and gates as implemented.
- Ensure every public path is nested under `/api/v1/orgs/{org_id}/repositories/{repository_id}`.
- Add request/response examples for policy defaults, repository config, policies, gates, validation, and effective config.

In Kong docs:

- Note that config/policies/gates route through `/api/v1/orgs/**` to the Organization / API Control Plane service.
- Do not add any top-level repository routes.

- [ ] **Step 4: Run BDD test**

Run:

```bash
mvn -pl services/organization -Dtest=RunOrganizationFeaturesTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/organization/src/test/resources/features services/organization/src/test/java/dev/vericov/organization/bdd docs/backend/services infra/kong/README.md
git commit -m "docs: document repository policy config APIs"
```

### Task 9: Verification Gate

**Files:**
- All files changed in this plan

- [ ] **Step 1: Run focused Organization tests**

Run:

```bash
mvn -pl services/organization test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run full Maven suite**

Run:

```bash
mvn test
```

Expected: BUILD SUCCESS for organization, upload, coverage-analysis, integrations, and root aggregator.

- [ ] **Step 3: Search for forbidden route shapes**

Run:

```bash
rg -n '/api/v1/(repositories|policies|gates)|/api/v1/badges/repositories' docs/backend infra services
```

Expected: no output.

- [ ] **Step 4: Review security-sensitive diff**

Run:

```bash
git diff -- services/organization infra/supabase docs/backend infra/kong
```

Check:

- No secrets or tokens added.
- No user-editable Supabase metadata used for authorization.
- Config values are deep-copied before storage.
- Viewer/developer roles cannot mutate config.
- SQL uses parameterized JDBC statements.
- New tables have RLS enabled and no public grants.

- [ ] **Step 5: Commit final adjustments**

```bash
git add services/organization infra/supabase docs/backend infra/kong
git commit -m "feat: add repository policy and gate config APIs"
```

## Self-Review Notes

- Spec coverage: The plan covers org defaults, repo config, policies, gates, effective config, validation, persistence, API routes, authz, tests, docs, and route-shape verification.
- Scope boundary: Actual coverage gate evaluation is explicitly deferred to Coverage Analysis.
- Route constraint: Every public repository-related endpoint remains nested under `/api/v1/orgs/{org_id}/repositories/{repository_id}`.
- Data safety: Maps/lists must be defensively copied and persisted as JSONB through structured JSON conversion, not hand-concatenated strings.
