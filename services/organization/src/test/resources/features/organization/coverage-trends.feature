@organization @coverage
Feature: Coverage trends

  Background:
    Given authenticated user "owner@example.com"
    And the current user created organization "Acme Engineering" with slug "coverage-trends"
    And the current user registers GitHub repository "acme/payments-api"

  Scenario Outline: Trends can be filtered by branch, metric, and date range
    Given a coverage report "abc122" on branch "main" created at "2026-05-22T08:00:00Z" exists with line 20/40, branch 2/10, function 2/5, and statement 10/25
    And a coverage report "abc123" on branch "main" created at "2026-05-22T09:00:00Z" exists with line 33/40, branch 8/10, function 5/5, and statement 20/25
    When the current user requests "<metric>" coverage trends for branch "main" from "2026-05-22T08:30:00Z" to "2026-05-22T09:30:00Z"
    Then the coverage trend contains only commit "abc123" for metric "<metric>" at "<percent>" percent

    Examples:
      | metric    | percent |
      | line      | 82.5    |
      | branch    | 80      |
      | function  | 100     |
      | statement | 80      |
