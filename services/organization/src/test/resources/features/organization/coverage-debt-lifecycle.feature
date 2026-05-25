@organization @api
Feature: Coverage Debt Lifecycle

  Background:
    Given authenticated user "owner@example.com"
    And the current user created organization "Acme Engineering" with slug "acme-debt"
    And the current user registers GitHub repository "acme/payments-api"

  Scenario: Create, read, update, list, resolve, and revoke coverage debt
    When the current user creates coverage debt for file "src/App.java" with risk "high" and reason "Need to address later" and owner "john@example.com"
    Then the coverage debt is successfully created with status "active"
    And the current user can retrieve details for the created coverage debt
    When the current user lists coverage debts with status "active"
    Then the list contains the created coverage debt
    When the current user updates the coverage debt owner to "jane@example.com" and reason to "Postponing logic coverage"
    Then the coverage debt details reflect the updated owner "jane@example.com" and reason "Postponing logic coverage"
    When the current user resolves the coverage debt
    Then the coverage debt status is "resolved"

  Scenario: Revoking coverage debt
    When the current user creates coverage debt for file "src/App.java" with risk "medium" and reason "Temporary bypass" and owner "john@example.com"
    Then the coverage debt is successfully created with status "active"
    When the current user revokes the coverage debt
    Then the coverage debt status is "revoked"

  Scenario: Viewers cannot create coverage debt
    And the current user adds user "viewer@example.com" as "viewer"
    And authenticated user "viewer@example.com"
    When the user "viewer@example.com" attempts to create coverage debt for file "src/App.java"
    Then the organization API rejects the request with status 403 and code "forbidden"
