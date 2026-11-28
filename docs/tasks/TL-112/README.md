# TL-112 — [DOCS] Reconcile native authentication with SRS v0.3

Jira: [TL-112](https://tama-nguyen.atlassian.net/browse/TL-112)
Observed 2026-11-28: Jira **In Progress**; production BFF session and browser OIDC decisions remain open.

## Impact index

| Document | Purpose and status | Affected areas | Relevant commits |
| --- | --- | --- | --- |
| [Authentication and current-user integration](plan-authentication-user-fe-be.md) | Implementation checkpoint plus unresolved release decisions | identity REST contract, native sessions, internal delegation, Next.js BFF, localized auth UI, Mailpit verification | `b7abc17`, `eda055d`, merge `0102f5e` |

Related Jira work: TL-113 backend lifecycle, TL-114 frontend integration and TL-115 end-to-end QA.
