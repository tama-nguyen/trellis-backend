<!--
Adapted from DataHub's .github/pull_request_template.md (Apache License 2.0,
https://github.com/datahub-project/datahub). Changes: GitLab terms, Trellis links, the
Conventional Commits title rule spelled out, and the Trellis test policy (decision T5).

Title: `<type>[(scope)][!]: <description>`, checked by the `mr:title` CI job.
Types: feat, fix, refactor, perf, docs, test, style, build, ci, chore.
The squash commit uses this title, and release notes are generated from it.
-->

## What and why

<!-- One or two sentences on the problem and the change. Link the issue: Closes #123 -->

## How to verify

<!-- Commands or steps a reviewer can run, e.g. `build-logic/scripts/dev/trellis-dev.sh up --profile app`. -->

## Checklist

- [ ] The title follows Conventional Commits (see the comment above); `!` if it breaks something
- [ ] Linked to the related issue (if any)
- [ ] Tests added or updated, **only** where the owner asked for them (AGENTS.md "Testing policy")
- [ ] Docs updated where behaviour, configuration or commands changed (`docs/`, module `AGENTS.md`)
- [ ] Breaking change, potential downtime or deprecation: entry added to `docs/how/breaking-changes.md`
- [ ] No secrets, real customer names or real hostnames in code, fixtures or this description
