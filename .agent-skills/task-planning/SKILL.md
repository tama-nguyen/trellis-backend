---
name: task-planning
description: >
  Create, move or update Jira-backed Trellis implementation plans and their impact indexes under
  docs/tasks. Use whenever work produces a plan, plan review, phased implementation note or
  repository evidence document; do not use for an ADR or ordinary guide that has no task owner.
---

# Maintain a task plan and impact index

Read [AGENTS.md](../../AGENTS.md#task-plans-and-impact-indexes) first. It owns the policy; this skill
owns the workflow.

## Multi-agent compatibility

Use ordinary Jira reads, repository files and git history. No agent-specific feature is required.
Jira writes remain separate external changes and need the authority implied by the user's request.

## Resolve the task before writing

Find the existing Jira issue and verify its key, exact title, status and scope. Do not use a
placeholder such as `TL-Example`, infer a key from a branch name, or silently create a ticket. If a
plan spans several tasks, choose the task that owns the decision and cross-link the others; split the
plan only when their implementation and acceptance can move independently.

Store the document at `docs/tasks/<JIRA-KEY>/<descriptive-name>.md`. Put the Jira link immediately
below its title. Plans are task evidence, not a second source of business truth: link to canonical
ADRs, guides and contracts instead of copying their rules.

## Maintain `README.md`

Create or update `docs/tasks/<JIRA-KEY>/README.md` in the same change. Its first heading is exactly:

```text
<JIRA-KEY> — <exact Jira title>
```

Record the Jira link and the date/status observed. Add one index row per plan, review or evidence
document with these fields: document, purpose/current status, affected modules or contracts, and
relevant commit SHAs. Mark historical or superseded documents explicitly and link the replacement.
Use neutral affected-area terms that later sessions can find with `rg` (module, table, API, event,
contract, configuration or deployment surface).

## Coordinate the branch

Use one owning Jira task per branch and name it `<type>/<JIRA-KEY>-<short-slug>`, for example
`feat/TL-123-question-variant-pilot`. After creating or first pushing it, add a Jira comment with
the repository and exact branch name/URL. Add the pull request to the same task when one exists.
Do not invent a ticket, rename a shared branch, or write Jira without the authority provided by the
owner or current task.

## Use the index for later work

Start impact analysis with `rg` across `docs/tasks/*/README.md`, then read only the matching task
documents. Confirm the current implementation in source before editing or reporting conclusions;
the index narrows the search but cannot establish present behavior.

Before finishing, verify every relative link after moving files, ensure `docs/plan/` is absent, run
`git diff --check`, and compare the task README's commit references with `git log`.
