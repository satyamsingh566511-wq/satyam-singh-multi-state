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
- Strict TypeScript + ESLint 9 + Vitest unit tests, all run in CI by a GitHub
  Action.

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
pnpm test       # Vitest (run mode)
pnpm codegen    # GraphQL Codegen → src/gql/generated/
```
