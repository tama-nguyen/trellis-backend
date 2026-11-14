# Trellis

Canonical guide for every agent (Claude Code, Cursor, Codex, ...) and for humans. `CLAUDE.md` only
imports this file. Module rules live in the module's own `AGENTS.md` and are not repeated here.

## What Trellis is

A reusable source base for large Java + Python + TypeScript monorepos, modelled on DataHub's
engineering standards. It holds **no business domain**: only the platform skeleton and one tiny sample
feature (`notice`) that shows every layer end to end. Products are generated from it; the token
`trellis` / `io.trellis` / `TRELLIS_` is what the future `init` script renames.

## Monorepo map

| Path             | What                                                               | Rules / docs                                       |
| ---------------- | ------------------------------------------------------------------ | -------------------------------------------------- |
| `web/`           | Next.js 15 frontend (App Router, TanStack Query, next-intl)        | `web/AGENTS.md`                                    |
| `backend/`       | Java modular monolith (Gradle, `build-logic/`)                     | `backend/AGENTS.md`, `docs/architecture/backend.md` |
| `workers/`       | Python workers and SDK (uv, ruff, mypy, pydantic)                  | `workers/AGENTS.md`, `docs/architecture/python.md` |
| `contracts/`     | Event JSON Schemas (+ committed OpenAPI); code generated from them | `contracts/` README / AGENTS.md (when added)       |
| `docker/`        | Images and local compose stack                                     | `docs/architecture/delivery.md`                    |
| `ci/`            | GitLab CI pipeline, Trivy config                                   | `docs/architecture/delivery.md`                    |
| `scripts/dev/`   | Local stack helper (`scripts/dev/trellis-dev.sh --help`)           | `docs/architecture/delivery.md`                    |
| `docs/`          | ADRs (`docs/adr/`), architecture, `docs/how/breaking-changes.md`   | -                                                  |
| `.agent-skills/` | Portable skills usable by any agent                                | `.agent-skills/README.md`                          |
| `.claude/`       | Claude Code only: settings, hooks                                  | -                                                  |

Tool versions are pinned in `mise.toml` (`mise install`). `.nvmrc` mirrors the Node version for nvm users.

## Essential commands (today)

Java runs through the root Gradle wrapper (`./gradlew`), Python through `uv` in `workers/`, and the
local stack through `scripts/dev/trellis-dev.sh`; see each module's rules for its commands. The
frontend runs on pnpm from `web/`:

```bash
cd web
pnpm install
pnpm dev                 # http://localhost:3000/vi
pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check
pnpm build && pnpm budget:check
pnpm format              # Prettier write
```

Run the tool through these scripts, not `npx <tool>` directly, so the pinned versions and config apply.

## Code standards (all languages)

- **Comments explain why**: an external constraint, a warning, a reference. Never restate the code.
  - Bad: `// increment retry count`
  - Good: `// The proxy drops idle sockets at 60 s, so retry once before surfacing the error.`
- **No magic string or number literals** for domain values, codes, statuses, header names, routes,
  storage keys. Put them in a named constant / enum owned by the module (`X.constants.ts`,
  `shared/constants/`). Exempt: UI copy (which goes through i18n anyway), log text, test fixtures.
- **Type everything.** TypeScript `strict` with no `any` (lint error); Python fully typed with no `Any`;
  Java no raw types. Prefer `type` over `interface` in TS unless declaration merging is required.
- **No barrel files** (`index.ts` that only re-exports). Import the file that owns the thing.
- **Generated files are never hand-edited.** Change the source (schema, contract, script) and regenerate.
  Generated output carries a header saying so.
- Validation and authorization live below the API layer; a hidden button is never authorization.

## Testing policy (decision T5)

- Infrastructure is in place: Vitest (unit) and Playwright (e2e) in `web/`.
- Tests exist **only for tooling** (ESLint rules, i18n parity script, bundle budget logic).
- **Do not write feature tests unless the owner asks.** This is a deliberate, temporary rule pending the
  owner's decision; see `../plan/decisions.md` T5.
- When tests are written: a test must be able to catch a real regression. Do not test framework
  behaviour, exact error message wording, or that an annotation exists.

## Commits and PRs

- Conventional Commits: `type(scope): description` (`feat(web): add notice detail page`).
  Types: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `ci`, `chore`.
- Breaking changes: `!` after the type and an entry in `docs/how/breaking-changes.md`, written for
  someone who does not read code.
- **Never use `--no-verify`.** A failing hook is a signal to stop and fix, not to skip. Report it and ask.
  `.claude/hooks/deny-git-no-verify.sh` enforces this for Claude Code.

## Confidentiality

No real customer names, hostnames, database or table names from real systems, or internal ticket IDs
in code, tests, fixtures, comments or commit messages. Use `example.test` domains and neutral sample data.

## Architecture decisions

Recorded as ADRs in `docs/adr/` (template in `0001`). A decision that changes how modules talk to each
other, or a toolchain choice, gets an ADR.
