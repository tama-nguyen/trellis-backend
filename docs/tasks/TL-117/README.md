# TL-117 — [BE] Verify security audit and permission resolution

Jira: [TL-117](https://tama-nguyen.atlassian.net/browse/TL-117)
Observed 2026-11-28: Jira **To Do**; several repository remediation phases are already implemented.

## Impact index

| Document | Purpose and status | Affected areas | Relevant commits |
| --- | --- | --- | --- |
| [Authorization phase plan](plan-phase1.md) | Executed plan; current account of database-backed scoped authorization | role/permission tables, grant resolution, course scope filters, ops APIs, audit | `38a5978`, `de8898f` |
| [Original authorization proposal](plan-phase1-authorization.md) | Historical proposal; superseded by `plan-phase1.md` | same authorization surface; use only for provenance | Consolidated in `0ab180b` |
| [Authorization review](review-phase1-authorization.md) | Historical staged-change review and repair order | clocks, grant SQL, bootstrap, migration runner, ops boundaries, cursor/filter types | Review baseline consolidated in `0ab180b` |
| [Security and identity remediation](plan-security-remediation.md) | Implemented phases with deployment follow-ups | trusted principals, JWT issuer, sessions, action tokens, durable mail, course permissions, security audit | `2faee3a`, `9181d38`, `1895e6b` |
| [Java and JUnit refactor](plan-refactor-security-tests.md) | Implemented behavior-preserving cleanup and verification | security/identity/course tests, fixtures, application workflow naming, action-token migration | `d756dc4`, evidence consolidated in `c66bafe` |

Use the affected-area column to select a document; do not read all five by default. Jira acceptance
still needs reconciliation with the repository evidence and deployment-only checks.
