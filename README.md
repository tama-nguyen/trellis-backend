# Trellis

A source base for large Java + Python + TypeScript monorepos, following DataHub's engineering standards.
It contains the platform skeleton and one small sample feature, no business domain. Products are
generated from it.

Today only the frontend (`web/`) exists; `backend/`, `workers/` and `contracts/` come next.
Read [AGENTS.md](AGENTS.md) before changing anything.

## Quick start (web)

Requires Node 22 and pnpm (`mise install` sets both up from `mise.toml`).

```bash
cd web
pnpm install
pnpm dev           # http://localhost:3000 -> redirects to /vi
```

The dev server runs against an in-browser mock backend (`NEXT_PUBLIC_MOCK_DOMAINS=true` in
`web/.env.development`). Sample accounts are listed in `web/src/mocks/auth.seed.ts`.

Quality gate: `pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm build && pnpm budget:check`.
