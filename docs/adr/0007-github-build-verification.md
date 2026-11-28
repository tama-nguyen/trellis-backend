# ADR 0007: GitHub Actions for build verification

Accepted 2026-11-28.

## Context

The three repositories are hosted on GitHub. Their GitLab pipelines assumed a registry, deployment
credentials and runner services that are no longer the intended delivery setup. The owner requests
build verification across backend, web and AI, with an optional manual test switch.

## Decision

Use repository-local GitHub Actions workflows on Ubuntu hosted runners for PRs, main pushes, merge
queues and manual runs. Automatic events always test; manual dispatch exposes boolean
`run_tests=true`. Skipping tests retains static checks, build and packaging.

Use each repository's existing locked tools and commands. Pin external actions by full SHA, update
them with Dependabot, grant read-only contents access and avoid persisted checkout credentials.
Build Docker images locally without registry writes. Upload reports and distributions for seven
days. No release, package publication or deployment is configured.

## Consequences

GitLab jobs and their registry-specific helpers are removed. Package publication is deferred and
consumers keep their existing snapshot mode. Branch protection must require successful automatic
checks; a manual run with tests disabled is build evidence only. Browser E2E is outside the build-only
workflow because its security/native authentication checks require an isolated backend and Mailpit.

Implementation and setup: [delivery guide](../architecture/delivery.md).
