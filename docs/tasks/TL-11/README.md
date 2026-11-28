# TL-11 — [BE] Establish backend platform foundation

Jira: [TL-11](https://tama-nguyen.atlassian.net/browse/TL-11)
Observed 2026-11-28: Jira **Done**; repository work below includes later foundation refinements.
Current branch: `feat/long-entity-identifiers` (created before the Jira-key branch convention).

## Impact index

| Document | Purpose and status | Affected areas | Relevant commits |
| --- | --- | --- | --- |
| [GitHub build verification](github-ci-verification.md), [ADR](../../adr/0007-github-build-verification.md) and [setup](../../architecture/delivery.md) | Build-only GitHub Actions across backend/web/AI; manual `run_tests` switch | workflows, Docker builds, contract drift, reports, registry publication deferred | Branch `ci/TL-11-github-actions` |
| [Long entity identifiers](plan-long-entity-identifiers.md) | Implemented; generated Long IDs for domain-owned JPA rows | identity users/sessions/actions/mail, course, notification, grants, cursors, contracts, generator | `8bb66d1`, `3bd9122`, `cc2c8db` |
| [Platform row identifiers](plan-long-platform-row-identifiers.md) | Deferred proposal; separate from the JPA migration | platform security role/grant JDBC rows, security-event audit IDs, Liquibase | Plan recorded with the long-ID architecture work |
| [JPA and query style](plan-query-style.md) | Implemented foundation and query conventions | Spring Data JPA adapters, native SQL exceptions, persistence rules, module generator | `25ae77f`, `84038c2`, `4228866`; plan consolidated in `0ab180b` |

Read the matching row before inspecting source. Verify current code and contracts because Jira's
baseline task predates some of these refinements.
