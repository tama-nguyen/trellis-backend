# TL-116 — [BE] Investigate role grant subject/resource integrity

Jira: [TL-116](https://tama-nguyen.atlassian.net/browse/TL-116)
Observed 2026-11-28: Jira **To Do**; repository evidence records implemented validation and checks.

## Impact index

| Document | Purpose and status | Affected areas | Relevant commits |
| --- | --- | --- | --- |
| [Grant targets and identity compatibility](plan-grant-targets-and-identity-compatibility.md) | Implemented validation; production legacy mapping remains deployment-specific | role grants, native/external subjects, OIDC identities, course resource scopes, migration operator SQL | `8c15919`, `c73104d` |

The implementation does not establish external issuer provenance for a real deployment; read the
plan's rollout boundary before changing stored grants.
