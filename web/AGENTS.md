# web/ - frontend rules

Module rules for the Next.js app. Repo-wide rules (comments, constants, commits, testing policy,
confidentiality) are in the root `AGENTS.md` and are not repeated here.

## Stack

Next.js 15 App Router, React 19, TypeScript 6 (`strict`, `noImplicitAny`, `noUncheckedIndexedAccess`),
TanStack Query 5, Tailwind CSS 4 with semantic tokens, Radix primitives under our own `ui/`, next-intl 4
(`vi` default, `en`, `zh`), lucide-react icons, Vitest, Playwright, ESLint 9 flat config, Prettier.
HTTP is native `fetch` behind one client; no axios, no second client.

## Commands

| Command                        | What                                                                      |
| ------------------------------ | ------------------------------------------------------------------------- |
| `pnpm dev`                     | Dev server with the in-browser mock backend (`.env.development`)          |
| `pnpm build` / `start`         | Production build / serve it                                               |
| `pnpm typecheck`               | `tsc --noEmit`                                                            |
| `pnpm lint` / `lint:fix`       | ESLint, zero warnings allowed                                             |
| `pnpm format` / `format:check` | Prettier (Tailwind classes sorted by the plugin)                          |
| `pnpm test:coverage`           | Vitest with coverage: `coverage/cobertura-coverage.xml` for CI            |
| `pnpm test`                    | Vitest: tooling tests (lint rules, scripts)                               |
| `pnpm test:e2e`                | Playwright; starts `pnpm dev` on port 3100. `--grep @p0` for the core set |
| `pnpm i18n:check`              | Locale parity (keys and `{placeholders}`)                                 |
| `pnpm budget:check`            | Bundle budget, after `pnpm build`                                         |
| `pnpm codegen`                 | Regenerate the API clients from `../contracts` (OpenAPI + GraphQL)        |
| `pnpm codegen:check`           | Fail with a diff when the committed generated clients are stale (CI)      |

Gate before pushing: `pnpm codegen:check && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm build && pnpm budget:check`.

## Layout and dependency direction

```text
src/
  app/[locale]/   routes only: thin pages that render a feature; _components/ = app chrome
  features/<f>/   one vertical slice per feature (components + feature hooks)
  entities/<d>/   one domain: <d>.types.ts, <d>.constants.ts, <d>.api.ts, <d>.queries.ts
  shared/         api/ (http, errors, queryClient), hooks/, lib/, constants/, types/
  ui/             design system on Radix + tokens; knows nothing about the app
  i18n/           next-intl routing, request config, messages/<locale>/<namespace>.json
  mocks/          in-browser mock backend, per domain
  styles/         tokens.css (raw colours, light + dark), globals.css (Tailwind theme)
```

`app -> features -> entities -> shared -> ui`. Never the other way. `ui/` may not import `app`,
`features`, `entities`, `shared/api` or `mocks` (lint: `local/ui-boundary`).

## Data pipeline

```text
component -> entity hook (<d>.queries.ts) -> entity api (<d>.api.ts)
          -> generated client (shared/api/generated, shared/graphql/executeGraphql) -> http (shared/api/http.ts)
```

- `http.ts` is the only caller of `fetch` (lint: `local/no-raw-fetch`). It adds the bearer token, runs
  a single-flight refresh on a stale-token 401, sends `FormData` untouched, and throws `ApiError`.
  Build URLs with `pathParams` / `params`, never by concatenation.
- `<d>.api.ts`: thin typed wrappers, no caching, no UI. Pass React Query's `signal` through.
- `<d>.queries.ts`: a **query-key factory** (`noticeKeys.all / list(params) / detail(id)`) and the hooks.
  Never inline a key; invalidate through the factory (`noticeKeys.all` clears the domain).
- Server state is never copied into `useState` or a store. Tokens are the one client-held auth state
  (`entities/auth/auth.session.ts`); the user comes from `useMe()`.
- Errors: `getApiErrorMessage(error, t, { codeMessages })` for inline/page errors,
  `useApiErrorHandler()` for toasts. The server's text is shown **only** for a validation problem
  (`type` = `PROBLEM_TYPES.VALIDATION`); everything else maps code -> status -> generic message.
  Per-code messages live in the entity: `AUTH_ERROR_MESSAGES`.
- Idempotent actions take their key from `useAttemptKey(scope)`: `current()` when submitting,
  `settle(!isOutcomeUnknown(err))` on error, `settle(true)` on success.
- Paged responses of the real backend are keyset pages, `CursorPage<T>` (`items`, `nextCursor`,
  `hasNext`; request with `first` + `after`). Use `useInfiniteQuery` and a "load more" control: there
  is no page number or total. `Page<T>` (0-based `page`) remains for offset endpoints (admin tables).
- Writes and integrations use REST (generated client); screen read models may use GraphQL
  (`/api/graphql`, typed documents next to the entity, e.g. `entities/notice/notice.graphql.ts`).

## Generated API clients

The backend publishes its contracts in `../contracts` (`./gradlew :trellis-app:exportApiContracts`
writes `contracts/openapi/openapi.json` and `contracts/graphql/schema.graphqls`). The web app never
hand-writes request or response types for them:

| Source                                                                   | Generator (pinned)                                            | Output                          |
| ------------------------------------------------------------------------ | ------------------------------------------------------------- | ------------------------------- |
| `contracts/openapi/openapi.json`                                         | orval 8.45.0, mutator `shared/api/apiRequest.ts`              | `src/shared/api/generated/`     |
| `contracts/graphql/schema.graphqls` + `graphql(...)` documents in `src/` | @graphql-codegen client-preset 6.2.3 (`documentMode: string`) | `src/shared/graphql/generated/` |

- Run `pnpm codegen` after the backend contract changes or after editing a `graphql(...)` document,
  and commit the output. CI runs `pnpm codegen:check` (regenerates into a temp dir, prints a diff).
- **Never edit generated files.** They are excluded from ESLint and Prettier; the script is
  `scripts/codegen.mjs`.
- Generated REST functions call `apiRequest` -> `http.ts`, so the token, refresh, `ApiError` and the
  BFF path apply unchanged; pass React Query's signal as the last argument (`{ signal }`).
- GraphQL documents are plain strings (no graphql-js in the bundle) run by
  `shared/graphql/executeGraphql.ts`, which turns `errors[0].extensions` into an `ApiError`
  (`extensions.code` -> HTTP-like status, so NOT_FOUND hits the 404 screen; `extensions.errorCode`
  -> `ApiError.code`, the same code REST returns).
- Entities re-export the generated shapes under domain names (`NoticeSummary`, `NoticeDetail`) and take
  enum values from the generated `as const` objects (`NOTICE_AUDIENCES = NoticeSummaryAudience`), so
  a contract change breaks the build instead of drifting. Mocks build their responses from the same
  types.

## Backend access and deployment

- **No backend URL is baked into the build.** The server reads `TRELLIS_BACKEND_URL` at runtime
  (`shared/api/backend.ts`); never add a `NEXT_PUBLIC_` backend URL, it would tie the image to one
  environment.
- **Browser:** calls same-origin `/api/...`. `app/api/[...path]/route.ts` proxies it to the backend
  (BFF), so there is no CORS and no backend origin in the bundle. Missing config answers 503
  `WEB_PROXY_NOT_CONFIGURED`, an unreachable backend 502 `WEB_PROXY_UNREACHABLE` (SCREAMING_SNAKE
  like the backend's `ErrorCode`s).
- **Server (Server Components, route handlers):** `http.ts` prefixes requests with the backend origin.
- `GET /api/health` is the container liveness probe: 200, no auth, no backend call.
- Container build: `TRELLIS_NEXT_OUTPUT=standalone` switches `next.config.ts` to `output: "standalone"`
  (`docker/web.Dockerfile`); leave it unset locally so `pnpm start` works.

## Mock backend

`NEXT_PUBLIC_MOCK_DOMAINS` = `true` | `false` | comma list (`auth,notice,config`), read only in
`mocks/domains.ts`. Handlers answer with the backend's contract, typed with the generated types:
`problem(path, status, CODE, detail)`, `pageOf(items, page, size, view)` for offset pages, cursor
pages built like `notice.handlers.ts`, and `guard(request, path, permission?)` for auth. A resource
the caller may not see (another user's, or a STAFF notice for a non-staff reader) answers **404, not
403**. The notice domain also mocks `POST /api/graphql` for its detail document. Mocks use the same
constants as the app, never bare literals. Sample accounts: `mocks/auth.seed.ts` (the member has
`notice:read`; the admin also `notice:read-staff` and `notice:manage`).

## React rules

- **No `useEffect` for derived state**, user-event reactions or state chaining. Compute during render;
  react in the event handler. Effects are only for external systems (navigation, analytics, storage
  events, subscriptions). The React Compiler lint rules (`react-hooks` v7) enforce much of this.
- **Five screen states** for every data-driven screen: loading (skeleton shaped like the content), empty
  (says how to move forward), error (mapped message + retry), not found (also used for "not yours"),
  ready. Use `PageShell` with `state={screenStateOf(query, { isEmpty })}`.
- Hooks in `shared/hooks/`: `useDebouncedCallback` (debounce only the expensive part),
  `useStableValue`, `useLatest`, `usePagination` / `useUrlPagination` (needs `<Suspense>`),
  `useAttemptKey`, `useFeatureFlag(key)` (typed keys, safe defaults, localStorage cache).
- Gate UI with `useCan().can(PERMISSIONS.X)` or `<RequirePermission>`. Hiding is not authorization.
- `"use client"` only where browser APIs or hooks require it. Pages stay server components.

## Auth

Tokens are kept in localStorage by `auth.session.ts`, which is acceptable for the mock only. When the
real backend lands: refresh token in an httpOnly cookie, and move the `(protected)` check into
`middleware.ts` so the page never renders for a signed-out user. `RequireAuth` stays as UX.

## i18n

- All user-visible copy goes through next-intl. Lint: `i18next/no-literal-string` (jsx-only mode, with
  an allow-list of non-copy attributes in `eslint.config.mjs`).
- One file per namespace per locale: `src/i18n/messages/<locale>/<namespace>.json`. Namespaces are named
  by area (`common`, `errors`, `auth`, `notice`), not by page. English is the reference shape and gives
  type-checked keys (`i18n/i18n.types.ts`).
- Placeholders are ICU: `{name}`, plurals `{count, plural, one {...} other {...}}`. Never build sentences
  by concatenation.
- Dates and numbers through `shared/lib/format.ts` with the current locale; never hardcode a locale.
- Link with `Link` / `useRouter` from `@/i18n/navigation` (keeps the locale prefix); `next/link` is
  banned by lint.

## Design tokens

All tokens live in `src/styles/tokens.css` (raw colour values, light `:root` + `.dark`) and
`src/styles/globals.css` (`@theme`: Tailwind utilities). Components use the utilities, never raw
values; `local/no-hardcoded-colors` rejects hex/rgb, palette classes, arbitrary colours and also
arbitrary or raw values that bypass the scales below (`z-[999]`, `z-50`, `duration-[350ms]`,
`duration-300`, `ease-[...]`, `animate-[...]`, `font-['Arial']`), naming the token to use.

| Family      | Tokens (utility)                                                                                                                                                                                                                                                                                   | Notes                                                                                                                                                                                                                                                                                                                                                 |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Colour      | `canvas`, `surface`, `surface-raised/-hover/-sunken`, `fg`, `fg-muted/-subtle/-brand`, `brand`, `brand-hover`, `on-brand`, `danger(-subtle)`, `on-danger`, `success(-subtle)`, `warning(-subtle)`, `info(-subtle)`, `border`, `border-strong`, `ring`, `overlay` (`bg-*`, `text-*`, `border-*`...) | values in `tokens.css`; the default palette is removed                                                                                                                                                                                                                                                                                                |
| Font        | `font-sans` (Inter -> system), `font-cjk` (Noto Sans SC -> PingFang SC, Hiragino Sans GB, Microsoft YaHei -> system), `font-mono` (JetBrains Mono -> system mono)                                                                                                                                  | next/font self-hosts the files at build (no runtime Google request). Body uses `font-sans`; `:lang(zh)` (zh pages and `lang="zh"` snippets on vi/en pages) uses `font-cjk`. Noto Sans SC is loaded only on zh pages (`ZhFontLoader`, a client `next/dynamic`), so /vi and /en never reference its files; snippets there fall back to system CJK fonts |
| Typography  | `<Prose>` (`ui/Prose.tsx`) = `prose prose-tokens max-w-none`                                                                                                                                                                                                                                       | @tailwindcss/typography; `prose-tokens` maps every `--tw-prose-*` to colour tokens, so it follows dark mode without `prose-invert`                                                                                                                                                                                                                    |
| Motion      | `duration-fast` 120ms, `duration-normal` 200ms, `duration-slow` 320ms; `ease-standard`, `ease-emphasized` (enter), `ease-exit`; `animate-fade-in` (overlays), `animate-scale-in` (Dialog), `animate-slide-in` (Toast)                                                                              | `prefers-reduced-motion: reduce` removes animations and transitions globally                                                                                                                                                                                                                                                                          |
| Z-index     | `z-base` 0, `z-dropdown` 10, `z-sticky` 20, `z-overlay` 40, `z-dialog` 50, `z-toast` 60, `z-tooltip` 70                                                                                                                                                                                            | low to high; a Select inside a Dialog passes `contentClassName="z-dialog"`                                                                                                                                                                                                                                                                            |
| Breakpoints | `sm` 40rem, `md` 48rem, `lg` 64rem, `xl` 80rem, `2xl` 96rem                                                                                                                                                                                                                                        | Tailwind's defaults, declared in `@theme` so they are owned here. Design for 375px first                                                                                                                                                                                                                                                              |
| Radius      | `rounded-control` 0.375rem, `rounded-card` 0.75rem, `rounded-dialog` 1rem                                                                                                                                                                                                                          |                                                                                                                                                                                                                                                                                                                                                       |
| Shadow      | `shadow-raised`                                                                                                                                                                                                                                                                                    | value per theme in `tokens.css`                                                                                                                                                                                                                                                                                                                       |

Adding a token: put the value in `tokens.css` (colours) or the `@theme` block (others), add a
`@utility` in `globals.css` when Tailwind has no theme namespace for it (z-index, duration), and
document it here. The lint rule reads the token names from `globals.css`.

## ui/ (design system)

- Built on Radix primitives and Tailwind token classes. Components take **already-translated** text via
  props; `ui/` never calls `useTranslations`.
- API conventions: a `XxxPropsDefaults` type + `xxxDefaults` object as the single source of defaults;
  native HTML props pass through (`...props`); `className` goes on the root element and is merged with
  `cn()`; inner parts get `*ClassName` props (`labelClassName`, `contentClassName`); `isLoading` shows a
  spinner and disables; variants via `cva`.
- Colours only through semantic tokens (`bg-surface`, `text-fg-muted`, `border-border`, `bg-danger-subtle`).
  Raw values live only in `src/styles/tokens.css`. Tailwind's default palette is removed from the theme.
- Toasts: `toast.success|error|info|warning|loading(message, { key })`; reuse a `key` to replace a
  loading toast with its result. `<ToastViewport>` is mounted once in `Providers`.
- Page containers use `max-w-7xl` (`PageShell` does this). Check phone width (375px) for every layout.

## File naming

- Components `PascalCase.tsx`; hooks `useThing.ts`; DataHub suffixes for the rest: `X.types.ts`,
  `X.constants.ts`, `X.api.ts`, `X.queries.ts`, `X.utils.ts`, `X.hooks.ts`.
- **No barrel files** (`index.ts`). Import the owning file.
- Tests in `__tests__/X.test.ts(x)` next to the code (tooling only for now, see root `AGENTS.md`).
- Generated files end in `.generated.ts`, are ignored by lint and never edited by hand.

## Lint rules

| Rule                                 | Enforces                                                                                       |
| ------------------------------------ | ---------------------------------------------------------------------------------------------- |
| `local/no-hardcoded-colors`          | no hex/rgb/hsl, no `bg-[#...]` / `bg-[var(...)]`, no palette classes; message lists the tokens |
| `local/ui-boundary`                  | `src/ui` imports nothing from app, features, entities, shared/api, mocks                       |
| `local/no-raw-fetch`                 | `fetch` only in `src/shared/api/http.ts`                                                       |
| `i18next/no-literal-string`          | no literal copy in JSX                                                                         |
| `@typescript-eslint/no-explicit-any` | no `any`                                                                                       |
| `consistent-type-definitions`        | `type`, not `interface` (except module augmentation)                                           |
| `no-restricted-imports`              | no barrels, no `next/link`, no axios                                                           |
| `react-hooks` (v7)                   | rules of hooks + React Compiler checks                                                         |

Local rules live in `eslint-rules/`, each with a RuleTester test in `eslint-rules/__tests__/`.

### Adopting a new rule gradually (git baseline)

`eslint-rules/git-baseline.mjs` (adapted from DataHub's `no-antd-imports`) lets a rule exempt files that
already broke it on the target branch, so a rule can land without fixing the whole codebase at once.
A rule opts in by accepting a `baseline` option (`no-raw-fetch` and `no-hardcoded-colors` do):

```js
"local/no-raw-fetch": ["error", {
  allow: ["src/shared/api/http.ts"],
  baseline: { grep: "fetch\\(", pathspec: "web/src" },   // git grep -E pattern
}],
```

Files matching `grep` on the baseline ref (`$TRELLIS_LINT_BASELINE_REF`, default `origin/HEAD`) are
exempt, renamed ones included; new files are not. If git cannot answer it fails open, so CI must set
the ref explicitly. Remove the `baseline` option once the last exempt file is fixed.

## Bundle budget

`scripts/budget.json` holds First Load JS (gzip, root + layouts + page) per route. Over the slack: warning;
over 10%: failure; shrunk beyond the slack: a notice to ratchet the budget down. Feature changes do not
raise budgets; record intended growth with `pnpm budget:check --update` in its own change.

## How to add...

**An entity** `entities/<d>/`: `<d>.types.ts`, `<d>.constants.ts` (API paths, `<D>_ERRORS`, enums as
`as const` objects, `<D>_ERROR_MESSAGES`), `<d>.api.ts`, `<d>.queries.ts` (key factory + hooks). Add mock
handlers in `mocks/<d>.handlers.ts`, a seed in `mocks/<d>.seed.ts`, a domain in `mocks/domains.ts` and
its group in `mocks/routes.ts`.

**A feature** `features/<f>/`: components that call entity hooks; derive the screen state with
`screenStateOf`; add a route `app/[locale]/<path>/page.tsx` that only renders the feature; add the path
to `ROUTES`; add a message namespace if needed (all three locales); add analytics events to
`shared/lib/analytics.ts` if the feature needs them.

**A locale**: add it to `LOCALES` and `INTL_LOCALES` in `i18n/routing.ts`, create
`messages/<locale>/` with every namespace, add its name to `common.locale` in every locale, run
`pnpm i18n:check`.

**A message namespace**: add it to `NAMESPACES` in `i18n/messages.ts` and to `AppMessages` in
`i18n/i18n.types.ts`, create the JSON in every locale.

**A feature flag**: add the key and its safe default to `FeatureFlags` / `DEFAULT_FEATURE_FLAGS` in
`shared/lib/featureFlags.ts`; read it with `useFeatureFlag("key")`. Check the flag-off branch.

**An e2e test**: `e2e/tests/<area>/<name>.spec.ts`, import `test` from `e2e/fixtures/base-test.ts`, use a
page object extending `BasePage`, tag with `TestTag.P0` if it guards the critical path, choose the user
with `test.use({ user: USER_KEYS.ADMIN })`.
