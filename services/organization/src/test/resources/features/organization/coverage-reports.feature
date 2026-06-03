@organization @coverage
Feature: Coverage reports

  Background:
    Given authenticated user "owner@example.com"
    And the current user created organization "Acme Engineering" with slug "coverage-reports"
    And the current user registers GitHub repository "acme/payments-api"
    And a pull request coverage report exists for commit "abc123" on branch "main"

  Scenario: Commit reports expose all metric summaries and files
    When the current user requests commit coverage report for "abc123" including files
    Then the commit coverage report exposes line "82.5", branch "80", function "100", and statement "80" percent
    And the commit coverage report contains file "src/App.java"

  Scenario: Pull request reports include diff coverage lines
    When the current user requests pull request 42 coverage report including diff lines
    Then the pull request coverage report includes "complete" diff coverage with 2 diff lines

  Scenario: Commit line hit maps are queryable by file
    When the current user requests coverage line hits for commit "abc123" file "src/App.java"
    Then line hits include file "src/App.java" line 14 with 0 hits
