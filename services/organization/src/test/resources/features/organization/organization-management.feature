@organization @api
Feature: Manage organizations and memberships

  Rule: Organization membership controls who can administer an organization

    Scenario: Authenticated user creates an organization as owner
      Given authenticated user "owner@example.com"
      When the user creates organization "Acme Engineering" with slug "acme" and plan "team"
      Then the organization API creates an active organization
      And the current user is an active owner member

    Scenario: Invalid organization slugs are rejected
      Given authenticated user "owner@example.com"
      When the user creates organization "Acme Engineering" with slug "Acme!" and plan "team"
      Then the organization API rejects the request with status 400 and code "validation_error"

    Scenario: Viewers cannot invite new members
      Given authenticated user "owner@example.com"
      And the current user created organization "Acme Engineering" with slug "viewer-authz"
      And the current user adds user "viewer@example.com" as "viewer"
      When user "viewer@example.com" invites "teammate@example.com" as "developer"
      Then the organization API rejects the request with status 403 and code "forbidden"

    Scenario: Invited users accept with a matching email and token
      Given authenticated user "owner@example.com"
      And the current user created organization "Acme Engineering" with slug "invite-flow"
      When the current user invites "member@example.com" as "developer"
      And user "member@example.com" accepts the invitation
      Then the invited user has an active "developer" membership

    Scenario: Authorization checks explain denied admin actions
      Given authenticated user "owner@example.com"
      And the current user created organization "Acme Engineering" with slug "authz-flow"
      And the current user adds user "viewer@example.com" as "viewer"
      When user "viewer@example.com" checks authorization for "org.members.invite"
      Then authorization is denied with code "forbidden"

    Scenario: Owners register repositories under an organization
      Given authenticated user "owner@example.com"
      And the current user created organization "Acme Engineering" with slug "repository-flow"
      When the current user registers GitHub repository "acme/payments-api"
      Then the repository API creates an active repository for the organization
