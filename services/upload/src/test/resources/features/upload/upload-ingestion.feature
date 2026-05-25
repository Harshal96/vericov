@upload @api
Feature: Upload coverage artifacts

  Rule: Accepted uploads are queued for analysis exactly once

    Scenario: Authorized repository upload queues coverage analysis
      Given repository "payments-api" accepts uploads on branch "main"
      And the upload request includes coverage and test-result artifacts
      When the repository submits the upload with idempotency key "upload-1"
      Then the API accepts the upload
      And the response contains a poll URL
      And upload status lists 2 stored artifacts
      And coverage analysis is queued once
      And an upload received event is published once

    Scenario: Repeating an idempotency key returns the existing upload
      Given repository "payments-api" accepts uploads on branch "main"
      And the upload request includes coverage and test-result artifacts
      When the repository submits the upload with idempotency key "upload-2"
      And the repository submits the upload with idempotency key "upload-2" again
      Then the API accepts the upload
      And the repeated upload returns the same upload id
      And upload artifacts are stored once
      And coverage analysis is queued once
      And an upload received event is published once

    Scenario: Uploads require create scope
      Given repository "payments-api" accepts uploads on branch "main"
      And the API key only has upload read scope
      And the upload request includes coverage and test-result artifacts
      When the repository submits the upload with idempotency key "upload-3"
      Then the API rejects the upload with status 403 and code "forbidden"
      And no upload side effects are recorded

    Scenario: Uploads are rejected outside the allowed branch
      Given repository "payments-api" accepts uploads on branch "main"
      And the API key only allows branch "release"
      And the upload request includes coverage and test-result artifacts
      When the repository submits the upload with idempotency key "upload-4"
      Then the API rejects the upload with status 403 and code "forbidden"
      And no upload side effects are recorded

    Scenario: Unsafe artifact names are rejected before storage
      Given repository "payments-api" accepts uploads on branch "main"
      And the upload request includes an artifact named "../lcov.info"
      When the repository submits the upload with idempotency key "upload-5"
      Then the API rejects the upload with status 400 and code "validation_error"
      And no upload side effects are recorded
