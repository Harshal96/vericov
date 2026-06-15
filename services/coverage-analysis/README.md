# Coverage Analysis Service

The coverage-analysis service is Vericov's internal worker. It consumes durable
upload jobs, parses coverage and test-result artifacts, writes normalized
reports, evaluates gates, and updates upload status.

## Processing Flow

```text
upload service
  -> PostgreSQL upload, artifact, analysis job, and PGMQ message
  -> coverage-analysis leases the queued job
  -> artifact bytes load from shared filesystem or Supabase Storage
  -> coverage and JUnit parsers normalize the inputs
  -> reports, file summaries, line hits, test runs, gaps, and gates persist
  -> upload service exposes the completed report
```

Supported coverage inputs include LCOV, Cobertura and coverage.py XML, JaCoCo
XML, Clover XML, Go cover profiles, and gcov-compatible text. JUnit XML is
supported for test results.

## Persistence

The worker uses PostgreSQL and PGMQ for leasing, retries, dead letters, and
state transitions. Summary data stays in PostgreSQL. Detailed normalized maps
are gzip-compressed in the configured artifact store.

The public two-service runtime does not depend on a control-plane or provider
integration service. Repository context is optional, and pull-request diff
processing is disabled unless a future local provider adapter is configured.

## Source Map

```text
application/     Worker loop and report processors
coverage/        Coverage parsers, merger, and normalized map serializer
testresults/     JUnit parser
gates/           Gate evaluation
gaps/            Gap extraction and risk scoring
diff/            Local diff coverage calculation
adapter/jdbc/    Queue, job, report, input, and test-run persistence
adapter/storage/ Filesystem and Supabase artifact access
config/          CDI wiring
```

## Tests

Run the module, including its 80% line-coverage gate:

```bash
mvn -pl services/coverage-analysis verify
```
