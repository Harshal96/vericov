# Monorepo Component Coverage Fixture

This fixture documents the expected ordering and assignment contract used by
the shared matcher, CLI snapshot, and analysis tests.

```yaml
version: 1

ignore:
  - generated/**
  - vendor/**
  - "!vendor/maintained/**"

components:
  - key: services
    name: Services
    gates:
      line: 80
    components:
      - key: payments
        name: Payments
        paths:
          - services/payments/**
      - key: payments-api
        name: Payments API
        gates:
          line: 90
        paths:
          - services/payments/api/**
```

| Source path | Expected result |
| --- | --- |
| `generated/client.js` | ignored before assignment |
| `vendor/dependency.js` | ignored before assignment |
| `vendor/maintained/adapter.js` | re-included, then `unassigned` |
| `services/payments/core.js` | `payments` |
| `services/payments/api/server.js` | `payments-api`, the more-specific leaf |
| `shared/logging.js` | `unassigned` |

The `services` parent includes both descendant leaves in its metrics. Its line
gate is inherited by both leaves, while `payments-api` overrides it with 90.
