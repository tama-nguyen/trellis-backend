# Trellis

A source base for large Java + Python + TypeScript monorepos, following DataHub's engineering
standards. It contains the platform skeleton and one small sample feature (`notice`), no business
domain. Products are generated from it.

Read [AGENTS.md](AGENTS.md) before changing anything.

## What lives where

Four source trees, one per runtime, plus the contracts that keep them agreeing with each other.

| Directory | Runtime | What it is | Rules |
| --- | --- | --- | --- |
| [`backend/`](backend/AGENTS.md) | Java 21, Spring Boot 4, Gradle | **The backend.** Owns the database and every business rule. Serves REST (commands, integrations) and GraphQL (read models for the UI), authorizes every call, writes the audit trail, and publishes domain events through a transactional outbox. | [`backend/AGENTS.md`](backend/AGENTS.md), [`docs/architecture/backend.md`](docs/architecture/backend.md) |
| [`web/`](web/AGENTS.md) | Node 22, Next.js 15, TypeScript | **The frontend.** App Router UI in vi/en/zh, its own design system (`web/src/ui`) on Tailwind tokens, TanStack Query over a generated API client. Also acts as the BFF: the browser only ever calls `/api/*` on this server, which forwards to the backend, so no API token reaches the browser. | [`web/AGENTS.md`](web/AGENTS.md) |
| [`workers/`](workers/AGENTS.md) | Python 3.12, uv workspace | **The event workers, SDK and CLI.** Everything that reacts to events or runs outside a request: Kafka consumers that build read models and do asynchronous work, a typed client for the backend API, and the `trellis` command line. This is also where data, batch and ML work belongs. | [`workers/AGENTS.md`](workers/AGENTS.md), [`docs/architecture/python.md`](docs/architecture/python.md) |
| `contracts/` | — | **The agreement between the three.** Event JSON Schemas, the exported OpenAPI document and GraphQL schema, shared test vectors. Java, TypeScript and Python clients are generated from these files and CI fails if a generated file drifts. Never hand-edit a generated file. | — |

Supporting directories: `docker/` (images and the local stack), `ci/` (GitLab pipeline),
`scripts/dev/` (`trellis-dev.sh`, the local stack helper), `docs/` (architecture and ADRs),
`.agent-skills/` and `.claude/` (agent tooling).

### How a change flows through them

A new feature usually touches all four, in this order:

1. `contracts/` — define or version the event payload.
2. `backend/domains/<name>/` — domain, use cases, SQL, REST and GraphQL, outbox event.
3. `workers/` — a consumer for the event, if something must happen asynchronously.
4. `web/` — regenerate the client, then build the screens.

### Backend module map

Gradle project names stay flat (`:trellis-app`) while directories say what each module is:

```text
backend/
├── common/            plain Java, no Spring: error model, context, ids, paging, AOP annotations
├── platform/
│   ├── base/          Spring infrastructure: persistence, authorization, observability, AOP aspects
│   ├── events/        outbox, relay, inbox, transports (Kafka or in-memory), dead letters
│   └── web/           REST, GraphQL, OpenAPI, the security filter chain
├── domains/notice/    the sample business module — copy this to start a real one
└── apps/app/          the deployable that assembles everything
```

A business module is layered inside: `api` (what other modules may import) → `domain` →
`application` → `infrastructure` → `web`. See [`backend/AGENTS.md`](backend/AGENTS.md).

## Quick start

Requires Java 21, Node 22 with pnpm, Python 3.12 with uv, and Docker. `mise install` sets the
versions from [`mise.toml`](mise.toml).

```bash
scripts/dev/trellis-dev.sh up --profile full   # postgres, kafka, redis, minio, keycloak, observability
```

```bash
./gradlew :trellis-app:bootRun                 # backend on :8080, management on :8081
```

```bash
cd web && pnpm install && pnpm dev             # http://localhost:3000 -> redirects to /vi
```

```bash
cd workers && uv sync && uv run trellis --help
```

The web dev server can run alone against an in-browser mock backend
(`NEXT_PUBLIC_MOCK_DOMAINS=true` in `web/.env.development`); sample accounts are in
`web/src/mocks/auth.seed.ts`.

## Quality gates

Each tree is checked on its own, and CI runs the same commands.

```bash
./gradlew build spotlessCheck :trellis-app:checkApiContracts
```

```bash
cd web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm build && pnpm budget:check && pnpm codegen:check
```

```bash
cd workers && uv lock --check && uv run ruff check && uv run mypy && uv run pytest
```
