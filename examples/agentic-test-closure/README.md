# Agentic Test Closure (Reference Example)

This is a copyable example, not shipped product code. It shows how to close
the loop from "Vericov reported a coverage gap" to "a test was written" using
a coding agent you run and pay for yourself. **Vericov produces the
evidence — the gap manifest — and nothing else in this loop.** The agent, its
execution environment, and its credentials belong entirely to you.

## What It Does

1. Your normal CI test job uploads coverage with `vericov upload --wait`
   (with a diff artifact attached, per the
   [PR diff coverage design](../../docs/superpowers/specs/2026-07-03-cli-supplied-pr-diff-coverage-design.md)).
2. If the reported `gate_status` is `failed`, a follow-up job fetches the
   gap manifest:
   ```bash
   vericov gaps --pull-request "$PR_NUMBER" --json --min-risk-level medium > manifest.json
   ```
3. The job runs Claude Code non-interactively, feeding it `manifest.json`
   and the pinned [`CLOSURE_PROMPT.md`](CLOSURE_PROMPT.md) in this directory.
   The agent writes tests that exercise the manifest's `uncovered_ranges`,
   touching only test files.
4. The job re-runs your test suite and re-uploads coverage. If the suite
   passes **and** patch coverage improved, it pushes a
   `test: close coverage gaps` commit to the pull request branch.
5. A human reviews that commit exactly like any other commit on the PR.
   **The workflow never merges anything.**

## What This Costs You

- One Claude Code invocation per gate failure per head commit, billed to
  **your** Anthropic API key (`ANTHROPIC_API_KEY`), configured as a repository
  secret in **your** CI. Vericov never sees this credential and has no
  billing relationship with it.
- Normal CI compute for the extra job (checkout, dependency install, test
  run, second coverage upload).

## Guardrails (Required, Not Optional)

If you adapt this example, keep every one of these:

- **Diff guard.** Before pushing, the workflow inspects the agent's produced
  diff and fails the job without pushing if it touches any non-test path.
  Test placement and scope are the agent's job; touching production code is
  not.
- **One attempt per head commit.** The pushed closure commit triggers a
  fresh Vericov upload for a new head commit, so the workflow naturally
  re-evaluates from there. The workflow does not re-enter itself: it checks
  for the `[vericov-test-closure]` sentinel in the latest commit message and
  exits immediately if present, so a closure commit's own upload can never
  trigger a second closure attempt.
- **Hard timeout and iteration cap.** The agent step has a fixed wall-clock
  timeout and a capped number of internal iterations (see
  `github-workflow.yml`). An agent that cannot converge fails loudly instead
  of running indefinitely.
- **No force pushes, no protected branches.** The workflow pushes only a
  normal commit to the existing PR branch and never targets `main` or any
  branch with protection rules.
- **Verification is Vericov's own next report.** The pushed commit's
  coverage upload is the single source of truth: it either clears the gate
  or it doesn't. There is no separate "agent says it worked" signal to
  trust.

## Security Posture

The agent step runs with repository **write** access in your CI, because it
needs to push a commit. Before using this in your repository:

- Pin every action to a full commit SHA, not a floating tag.
- Restrict the workflow to pull requests from branches in the same
  repository. **Never** use `pull_request_target` with this workflow against
  forked pull requests — that combination hands write access and secrets to
  arbitrary external contributors.
- Scope the CI token (`permissions:` in the workflow) to `contents: write`
  and nothing broader.
- Treat `ANTHROPIC_API_KEY` like any other repository secret: restrict which
  branches/environments can access it.

## Files

- [`github-workflow.yml`](github-workflow.yml) — the GitHub Actions workflow.
- [`CLOSURE_PROMPT.md`](CLOSURE_PROMPT.md) — the pinned instruction file
  given to the agent verbatim. Version it like code; changing it changes
  what the agent is allowed to do.
