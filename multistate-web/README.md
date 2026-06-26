# multistate-web

The web front end for the Multi-State Tax Compliance Tracker — a Vite + React 19
+ TypeScript app. The backend domain library and Spring Boot service live in the
repo root; see the [root README](../README.md) for that side.

## What's here

- A `TenantDetailPage` driven by a **`useReducer` state machine**
  (`idle → loading → success | empty | error`) with a `never` exhaustiveness
  check on the action union, so each render branch is narrowed by TypeScript.
- A **Zustand filter store** (`useTenantFilterStore`) with `devtools` + `persist`
  middleware. Each `FilterStrip` control subscribes to only its own slice, and
  `partialize` persists *only* `threshold` to `localStorage` — search and chips
  are session-only by design.
- A **`useDebouncedSearch`** hook that lags the store's search text by 300ms and
  clears its timer on every keystroke and on unmount.
- An **`ErrorBoundary`** wrapping the route with a render-prop fallback and a
  retry that re-mounts the subtree (exercised by a DEV-only "Trigger error"
  button that throws during render).
- A full **test pyramid** — RTL + Vitest component/integration tests with MSW,
  jest-axe accessibility assertions, and a Playwright Chromium E2E happy-path —
  behind a single `pnpm check` gate (typecheck + lint + coverage + E2E) run in
  CI by a GitHub Action.
- Strict TypeScript + an **ESLint 9 flat config** (type-checked rules, React
  Hooks, `jsx-a11y`, and explicit `no-explicit-any` / `as any` bans).

## Week 4 Day 3 — data layer, routing & API mocking

This day wired the app to its GraphQL + REST backends and replaced the W4 D1/D2
hand-rolled hash router with a real router.

- **Apollo Client + JWT auth** (`src/apollo/client.ts`): an `ApolloClient` over
  an `HttpLink` to `http://localhost:8080/graphql`, an `InMemoryCache` whose
  `typePolicies` normalise `Tenant` by `keyFields: ['id']`, and a `setContext`
  link that attaches `Authorization: Bearer <jwt>` from
  `localStorage.getItem('uc:jwt')`. (Threat model: the token in `localStorage`
  is XSS-exposed; the HttpOnly-cookie story is deferred to W6.)
- **GraphQL Codegen** (`codegen.ts`) with the **client-preset**, generating
  typed documents into `src/gql/generated/` from `src/queries/**/*.graphql`
  against a local SDL (`schema.graphql`) so codegen runs without a live backend.
  `src/gql/operations.ts` adds thin, fully-typed `useLatestTenantsQuery` /
  `useSummarizeTenantMutation` wrappers over those documents (Apollo Client v4
  moved its hooks to `@apollo/client/react`).
- **`TenantListPage`** — `useLatestTenantsQuery()` with `loading` / `error` /
  empty-array render branches.
- **`TenantSummaryPage`** — `useSummarizeTenantMutation` with an
  `optimisticResponse` (tagged `__typename: 'TenantSummary'`) that paints a
  placeholder card immediately, then swaps in the server result.
- **TanStack Query** (`src/queryClient.ts`, `useGetMultiStateRest`) — a
  `QueryClient` with a 60s `staleTime`, and a REST hook keyed
  `['multistate', id]` against `/api/v1/tenants/{id}`.
- **React Router v7** (`src/router.tsx`) — `createBrowserRouter` with a
  `<ProtectedLayout>` that redirects to `/login` (via `<Navigate replace>`) when
  `uc:jwt` is absent, otherwise renders the `/tenants*` routes. `main.tsx` now
  nests `<ApolloProvider>` + `<QueryClientProvider>` around `<RouterProvider>`.
- **MSW + Vitest** (`src/test/handlers.ts`, `src/test/server.ts`) — GraphQL and
  REST request mocks with `setupServer` + `beforeAll/afterEach/afterAll` and
  `onUnhandledRequest: 'error'`. New tests cover the tenant list, the optimistic
  summary swap, the protected-route redirect/pass-through, and the REST hook —
  bringing the suite to **20 tests**.

## Week 4 Day 4 — streaming assistant chat (Vercel AI SDK + Hono proxy)

This day added a streaming tenant-assistant chat: a Hono proxy that fronts the
W3 D4 Spring AI endpoint and a `useChat` panel that renders tokens as they
arrive. The Vercel AI SDK is pinned to the **v4 generation** (`ai@4`,
`@ai-sdk/react@1`, `@ai-sdk/openai-compatible@0.2`, `zod@3`) — the API surface
this exercise targets (`toDataStreamResponse`, `useChat`'s
`input`/`handleSubmit`/`isLoading`/`reload`, `m.content`, `m.toolInvocations`).

- **Hono proxy** (`server/index.ts`, `server/api/chat.ts`) — a `POST /api/chat`
  route that reads `messages`, calls `streamText` against the Spring AI endpoint
  via `@ai-sdk/openai-compatible`, and returns `result.toDataStreamResponse(...)`
  with streaming-safe headers (`text/event-stream`, `no-cache, no-transform`,
  `X-Accel-Buffering: no`). It forwards `c.req.raw.signal` as `abortSignal` so an
  aborted browser fetch also stops the upstream LLM call, and a `getErrorMessage`
  mapper turns an upstream 4xx/5xx into a clean sentinel error frame instead of a
  torn connection. The proxy holds the upstream credentials so the browser never
  sees them; it runs on `:3001` via `@hono/node-server`.
- **Zod-typed tools** (`server/api/chat-tools.ts`) — `lookupTenant` and
  `nexusForState`, each with a `zod` `parameters` schema and an `execute` that
  hits the W3 D2 REST backend. They're passed into `streamText` with
  `maxSteps: 3` so the assistant can chain a tool call into a final reply.
- **`TenantChatPanel`** (`src/pages/TenantChatPanel.tsx`) — a
  ``useChat({ api: '/api/chat', id: `tenant-${id}` })`` panel rendering a
  `<ul aria-label="chat-transcript">` with `data-role` per message. UX polish: a
  `role="status"` spinner while `isLoading`, a `role="alert"` pane on `error`,
  Stop/Regenerate buttons (wired to `stop` / `reload`, disabled by streaming
  state), a Send button disabled on empty input, and a `useEffect` + ref that
  `scrollIntoView`s on each `messages` change.
- **`ToolCallCard`** (`src/pages/ToolCallCard.tsx`) — a presentational
  `<aside aria-label="tool-call" data-state={…}>` showing the tool name and args,
  plus a `data-testid="tool-result"` payload once the invocation reaches the
  `result` state. The panel maps `message.toolInvocations ?? []` beneath each
  message.
- **Chat persistence** (`src/stores/useTenantChatStore.ts`) — a Zustand `persist`
  store (`name: 'uc:tenant-chat'`) whose `appendAssistantMessage` is called
  **only** from `useChat`'s `onFinish`, so partial / Stopped replies never reach
  `localStorage` and the completed transcript rehydrates cleanly on reload.
- **Routing** — `src/router.tsx` mounts `<TenantChatPanel>` at
  `/tenants/:id/chat` under the protected layout, and `TenantSummaryPage` links
  to it.
- **Vite proxy** (`vite.config.ts`) — forwards the browser's `/api/chat` to the
  Hono server on `:3001`; `pnpm dev` now runs Vite and the proxy together.
- **MSW SSE + Vitest** (`src/test/sse-handlers.ts`) — a hand-rolled
  `http.post('/api/chat')` returning a `ReadableStream` of v4 data-stream frames
  (`0:"…"` text deltas + a `d:{…}` finish frame) with the
  `X-Vercel-AI-Data-Stream: v1` header, spread into `handlers.ts`. Four new test
  files cover streamed-token rendering, Stop-mid-stream, Regenerate, the three
  `ToolCallCard` states, the persist round-trip, and the upstream-500
  `role="alert"` path. `src/test/setup.ts` gains a `scrollIntoView` shim and an
  abort bridge (scoped to `/api/chat`) so `stop()` is testable under jsdom's
  AbortSignal. The suite climbs to **42 tests**.

## Week 4 Day 5 — the test pyramid: RTL + MSW integration + Playwright E2E + a11y gate

This day turned the app's ad-hoc unit tests into a graded, CI-enforced testing
pyramid and added the `/login` page the E2E flow logs in through. The single
entrypoint is `pnpm check` (`tsc --noEmit && eslint . && vitest run --coverage
&& playwright test`), wired into the W4 D1 GitHub Action.

- **Vitest harness** (`vitest.config.ts`, `src/test/setupTests.ts`,
  `src/test/renderWithProviders.tsx`) — `environment: 'jsdom'`, a single
  `setupFiles` module that loads `@testing-library/jest-dom`, extends `expect`
  with `toHaveNoViolations`, and binds the MSW `beforeAll/afterEach/afterAll`
  lifecycle. `renderWithProviders` mounts every provider a page needs
  (`MockedProvider` + `QueryClientProvider` + `MemoryRouter`) and returns one
  `userEvent.setup()` instance per render. A `coverage.thresholds.branches` of
  **70** is the load-bearing gate.
- **Component tests** (`src/pages/TenantListPage.test.tsx`,
  `src/pages/TenantSummaryPage.test.tsx`) — role-first specs
  (`getByRole` / `findByRole` as the primary query) covering the list, summary,
  loading, empty, and error branches, each ending with an
  `expect(await axe(container)).toHaveNoViolations()` assertion.
- **MSW integration tests** (`src/pages/TenantSummaryPage.integration.test.tsx`,
  14 tests) — drive the real query hook + cache + filter store against MSW,
  covering the REST happy path, the REST 500 path, the loading skeleton, the
  filter-store ↔ REST integration, the empty state, and a cache-hit / warm-mount
  path, using `findBy*` for everything async. `src/test/handlers.ts` now exports
  explicit **happy / error / loading** handlers for *both* the REST endpoint
  (`tenantRestHandlers`, `tenantErrorHandler`, `tenantLoadingHandler`) and the
  Apollo `LatestTenants` query (`latestTenantsErrorHandler`,
  `latestTenantsLoadingHandler`), so a test can flip a single endpoint with
  `server.use(...)`.
- **Playwright E2E** (`playwright.config.ts`, `e2e/global-setup.ts`,
  `e2e/tenant-chat.spec.ts`) — `testDir: './e2e'`, `fullyParallel`,
  `retries: process.env.CI ? 2 : 0`, a `chromium` project, a `webServer` booting
  `pnpm dev`, and `use.storageState` pointing at `e2e/.auth/user.json`. A global
  setup logs in once through the UI and persists that storage state; the spec
  then opens the tenant list, drills into a row by accessible name, drives the
  W4 D4 chat panel, asserts the streamed tokens land in `getByRole('log')` and
  the tool-call card is visible, reloads, and asserts the conversation persists.
  Every backend hop is mocked at the browser edge with `page.route`.
- **Accessibility** — jest-axe in the component tests plus
  `@axe-core/playwright`'s `AxeBuilder().withTags(['wcag2a', 'wcag2aa'])` on the
  detail page in the E2E run, both asserting zero violations. The streaming
  transcript was promoted from `<ul aria-label="chat-transcript">` to a
  `<div role="log" aria-label="chat-transcript">` live region (plain `<div>`
  rows, so no orphaned `listitem` roles), which is both the correct semantics
  for streamed output and what the E2E `getByRole('log')` assertion targets.
- **ESLint 9 flat config** (`eslint.config.js`) — `js.configs.recommended`,
  `tseslint.configs.recommendedTypeChecked`, `react-hooks/recommended`,
  `jsx-a11y/recommended`, and explicit bans on `@typescript-eslint/no-explicit-any`
  plus `as any` (matched syntactically via `no-restricted-syntax`), with a
  test-file block relaxing the dynamic-matcher `no-unsafe-*` rules.
- **`LoginPage`** (`src/pages/LoginPage.tsx`, `+ .test.tsx`) — the real sign-in
  form the E2E `global-setup` authenticates through.
- The suite now stands at **82 Vitest tests across 17 files** plus the Playwright
  Chromium happy-path, with branch coverage at ~88%.

## Run it

Requires Node 20 (see [.nvmrc](.nvmrc)) and [pnpm](https://pnpm.io)
(version pinned via the `packageManager` field in `package.json`).

```sh
pnpm install
pnpm dev         # Vite (http://localhost:5173) + Hono chat proxy (http://localhost:3001), together
```

`pnpm dev` runs both processes via `concurrently`; the Vite dev server proxies
`/api/chat` to the Hono server. With `localStorage` empty you land on `/login`;
"Sign in (stub)" writes a fake `uc:jwt` and routes to `/tenants` (no `#` in the
URL — `createBrowserRouter` uses the History API). Individual tenants live at
`/tenants/<id>`, `/tenants/<id>/summary`, and `/tenants/<id>/chat` (the
streaming assistant — needs the W3 D4 Spring AI endpoint on `:8080` for live
replies).

## Other scripts

```sh
pnpm server     # Hono chat proxy only (tsx watch) on http://localhost:3001
pnpm build      # type-check, then production build
pnpm preview    # serve the production build locally
pnpm lint       # ESLint 9
pnpm typecheck  # tsc --noEmit
pnpm test       # Vitest (run mode) with coverage
pnpm e2e        # Playwright (boots pnpm dev via its webServer)
pnpm check      # the full CI gate: tsc --noEmit && eslint . && vitest --coverage && playwright test
pnpm codegen    # GraphQL Codegen → src/gql/generated/
```
