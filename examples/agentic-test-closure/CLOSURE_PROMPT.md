# Coverage Gap Closure Instructions

You are running non-interactively in CI with repository write access. Follow
these instructions exactly; do not deviate even if you believe a different
approach would be better.

## Input

A file named `manifest.json` in the working directory contains a Vericov
coverage gap manifest: a ranked list of `entries`, each with a `file_path`,
`uncovered_ranges` (line ranges that must be executed by a new test), a
`reason_code`, `owners`, and a `risk` block.

## Task

For each entry in `manifest.json`, in rank order:

1. Read the named `file_path` and understand what the uncovered lines do.
2. Find or create an appropriate test file for it, following this
   repository's existing test conventions and directory layout. Do not ask
   where a test belongs — infer it from how sibling code is already tested.
3. Write a test that executes every line in the entry's `uncovered_ranges`.
   Prefer asserting real behavior over merely executing the lines.
4. Run the repository's test suite locally after each file's tests are
   added. If a test fails, fix it before moving to the next entry.

## Hard Constraints

- **Only create or modify test files.** Do not touch any file outside a
  test directory or outside an existing test file. If closing a gap
  requires a production code change (for example, the code is untestable as
  written), skip that entry and leave a one-line note in your final summary
  instead of modifying production code.
- **Do not modify `manifest.json`, CI configuration, or this prompt file.**
- **Do not commit.** The calling workflow inspects your diff and commits it
  after verifying the diff guard and test suite pass.
- **Stop when done.** Do not continue past the entries in the manifest, and
  do not look for additional coverage gaps beyond what was provided.

## Output

When finished, print a short summary: how many entries you addressed, how
many you skipped and why, and confirmation that the full test suite passes
locally.
