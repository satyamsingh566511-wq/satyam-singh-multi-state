# multistate-web

The web front end for the Multi-State Tax Compliance Tracker — a Vite + React 19
+ TypeScript app. The backend domain library and Spring Boot service live in the
repo root; see the [root README](../README.md) for that side.

## What's here

- A `TenantDetailPage` that loads tenant data through the `useTenant` hook and
  demonstrates **lifted state**: the page owns a `threshold` value that a
  controlled `ThresholdSlider` mutates and a sibling `ThresholdReadout` reads —
  two siblings, one source of truth.
- A hand-rolled hash router (`App.tsx`) that matches a single route off
  `window.location.hash`. TanStack Router lands on W4 D3; until then this keeps
  the dev server deep-linkable with no router dependency.
- Strict TypeScript + ESLint 9 + a Vitest smoke test, all run in CI by a GitHub
  Action.

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
