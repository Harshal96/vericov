@analysis @coverage
Feature: Analyze uploaded coverage

  Rule: Upload received messages drive exactly one analysis decision

    Scenario: Coverage artifacts are merged, gates are evaluated, and the analysis job completes
      Given an upload received message with LCOV coverage artifacts
      And object storage contains the LCOV shards
      And an active project coverage gate requiring 95 percent line coverage
      When the analysis worker polls once
      Then the coverage report is persisted with 3 covered lines out of 3
      And a normalized coverage map is stored
      And a passed line coverage gate evaluation is persisted
      And the analysis job is completed
      And the queue message is archived

    Scenario: Uploads without coverage artifacts are retried
      Given an upload received message without coverage artifacts
      When the analysis worker polls once
      Then the analysis job failure is recorded
      And the queue message is rescheduled
      And no coverage report is persisted

    Scenario: Mixed coverage artifact formats are merged
      Given an upload received message with LCOV and JaCoCo coverage artifacts
      And object storage contains the mixed coverage shards
      When the analysis worker polls once
      Then the coverage report is persisted with 3 covered lines out of 3
      And the analysis job is completed
      And the queue message is archived

    Scenario: Busy jobs are rescheduled without processing
      Given an upload received message with LCOV coverage artifacts
      And object storage contains the LCOV shards
      And the analysis job is already locked
      When the analysis worker polls once
      Then the queue message is rescheduled
      And no coverage report is persisted
      And the coverage artifacts are not downloaded

    Scenario: Unsupported events are dead-lettered
      Given an unsupported analysis message
      When the analysis worker polls once
      Then the queue message is moved to the dead-letter queue
      And the queue message is archived
      And analysis processing is not started
