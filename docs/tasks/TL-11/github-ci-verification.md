# GitHub build verification across three repositories

Jira: [TL-11](https://tama-nguyen.atlassian.net/browse/TL-11)

Implemented locally on `ci/TL-11-github-actions` in backend, web and AI. Inspected 2026-11-28.
GitHub-hosted execution is tracked through the integration PRs; local results below precede those runs.

## Scope and impact

Replace the GitLab pipelines with `.github/workflows/ci.yml` and weekly action updates. Remove
backend GitLab includes, release/deploy configuration and registry-only CI helpers. Keep the
local Compose stack and smoke helper. Static checks, packages and local container builds remain
active when manual `run_tests=false`; automatic events always test. Tool versions are read from
mise/package manifests, while action SHAs remain explicit dependency pins.

Web's contract downloader now uses GitHub with the backend repository and full schema SHA in
`scripts/contracts.json`. The pin advances past the notification rename to the existing pushed
backend commit `0b8f101`. Existing generated web changes were preserved. Backend's staged Qodana
files are concurrent owner work and outside this migration.

## Verification

| Area | Command or check | Observed result |
| --- | --- | --- |
| Workflow syntax | actionlint 1.7.12 against all three new workflows | Passed |
| Backend full gate | JDK 21 `./gradlew --no-daemon --stacktrace --continue build jacocoTestReport` | BUILD SUCCESSFUL; includes API contract check |
| Backend tests disabled | `./gradlew --no-daemon --stacktrace build -x test -x checkApiContracts` | BUILD SUCCESSFUL |
| Backend clients | `build-logic/scripts/contracts/generate.sh --check`, frozen pnpm install/build, `uv build --project contracts/packages/python` | Passed |
| Web gate | contract download/check, typecheck, ESLint, Prettier, i18n, Vitest coverage, Next build, bundle budget | Passed; 76 unit tests; bundle warnings below failure threshold |
| AI gate | lock check, locked sync, Ruff, mypy, pytest coverage, Postgres integration, `uv build --all-packages` | Passed; 43 tooling/contract tests and 5 integration tests |
| Containers | Existing Dockerfiles built locally in all three repos | Passed; no registry push |
| Optional browser trial | Existing Playwright suite without real backend/Mailpit | Six pass, one flaky pass, one skip, security-boundary fails; excluded from build-only CI |

The AI environment had relocated console-script shebangs from an earlier checkout path. Reinstalling
the locked mypy/pytest packages repaired that local environment; no dependency version changed.

Setup and check names: [backend delivery guide](../../architecture/delivery.md), sibling web/AI
`docs/ci.md`. GitHub run outcomes are available on the PR check tabs and are separate from these local
results. No release/package publish/deployment is implemented.
