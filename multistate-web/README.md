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
- A hand-rolled hash router (`App.tsx`) that matches a single route off
  `window.location.hash`. TanStack Router lands on W4 D3; until then this keeps
  the dev server deep-linkable with no router dependency.
- Strict TypeScript + ESLint 9 + Vitest unit tests (reducer, store, debounce
  hook), all run in CI by a GitHub Action.

## Run it

Requires Node 20 (see [.nvmrc](.nvmrc)).

```sh
npm install
npm run dev      # Vite dev server on http://localhost:5173
```

Then open the tenant page directly at
<http://localhost:5173/#/tenants/stub-id-1>.

## Other scripts

```sh
npm run build      # type-check, then production build
npm run preview    # serve the production build locally
npm run lint       # ESLint 9
npm run typecheck  # tsc --noEmit
npm test           # Vitest (run mode)
```
