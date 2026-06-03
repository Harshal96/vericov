@organization @coverage
Feature: Gate evaluation visibility

  Background:
    Given authenticated user "owner@example.com"
    And the current user created organization "Acme Engineering" with slug "gate-evaluations"
    And the current user registers GitHub repository "acme/payments-api"

  Scenario: Project gates can be configured for every coverage metric
    When the current user configures project coverage gates for all coverage metrics
    Then repository gates include metrics line, branch, function, and statement

  Scenario: Persisted gate evaluations can be filtered by status
    Given gate evaluations exist for passed branch and failed line coverage
    When the current user lists gate evaluations with status "failed"
    Then gate evaluations include "Project line coverage" for metric "line" with status "failed"
    When the current user lists gate evaluations with status "passed"
    Then gate evaluations include "Project branch coverage" for metric "branch" with status "passed"
