@organization @coverage
Feature: Coverage badges

  Background:
    Given authenticated user "owner@example.com"
    And the current user created organization "Acme Engineering" with slug "coverage-badges"
    And the current user registers GitHub repository "acme/payments-api"
    And a coverage report "abc123" on branch "main" created at "2026-05-22T10:00:00Z" exists with line 33/40, branch 8/10, function 5/5, and statement 20/25

  Scenario: Token access returns SVG and JSON coverage badges
    When the current user enables coverage badge metric "line" on branch "main"
    And the current user rotates the coverage badge token
    And an unauthenticated client requests coverage badge JSON for metric "line" with the token
    Then the coverage badge response has message "82.5%" and color "green"
    When an unauthenticated client requests coverage badge SVG for metric "line" with the token
    Then the coverage badge SVG contains "82.5%"

  Scenario: Authenticated users can request non-default badge metrics
    When the current user enables coverage badge metric "line" on branch "main"
    And the current user requests authenticated coverage badge JSON for metric "function"
    Then the coverage badge response has message "100%" and color "brightgreen"
