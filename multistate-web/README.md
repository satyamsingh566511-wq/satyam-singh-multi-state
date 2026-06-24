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

## Run it

Requires Node 20 (see [.nvmrc](.nvmrc)).

```sh
npm install
npm run dev      # Vite dev server on http://localhost:5173
```

With `localStorage` empty you land on `/login`; "Sign in (stub)" writes a fake
`uc:jwt` and routes to `/tenants` (no `#` in the URL — `createBrowserRouter`
uses the History API). Individual tenants live at `/tenants/<id>` and
`/tenants/<id>/summary`.

## Other scripts

```sh
npm run build      # type-check, then production build
npm run preview    # serve the production build locally
npm run lint       # ESLint 9
npm run typecheck  # tsc --noEmit
npm test           # Vitest (run mode)
npm run codegen    # GraphQL Codegen → src/gql/generated/
```
