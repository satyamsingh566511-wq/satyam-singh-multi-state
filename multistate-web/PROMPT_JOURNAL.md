# Prompt Journal — multistate-web

A log of the AI prompts that produced **shipped** code, the model that answered,
a summary of what it produced, and my verdict (accept / accept-with-edits /
reject) with the reasoning. Model for all W4 D4 entries: **Claude Opus 4.8**
(`claude-opus-4-8`), via Claude Code.

---

## Week 4 Day 4 — streaming assistant chat (2026-06-25)

### Entry 0 — SDK version reconciliation (decision that shaped every file below)

- **Prompt:** "Build Tasks 1–4 inside `multistate-web/`" — the reference
  snippets were written against the AI SDK **v3/v4** API (`toDataStreamResponse`,
  `useChat` returning `input`/`handleSubmit`/`isLoading`/`reload`, `m.content`,
  `m.toolInvocations`, `maxSteps`, `X-Vercel-AI-Data-Stream: v1`).
- **Model output:** Probed `node_modules` and found the *installed* packages were
  the **v6 generation** (`ai@6`, `@ai-sdk/react@3`, `@ai-sdk/openai-compatible@2`)
  whose API is incompatible (`toUIMessageStreamResponse`, `sendMessage`/`status`,
  `parts[]`, `stopWhen`). It flagged that the reference code would not typecheck
  or run as-is and surfaced a binary choice: implement on v6, or downgrade deps to
  the v4 generation the reference targets.
- **Verdict:** **Accepted the flag; chose downgrade.** Pinned `ai@^4`,
  `@ai-sdk/react@^1`, `@ai-sdk/openai-compatible@^0.2`, `zod@^3` so the reference
  API is real. Accepting the v6 install silently would have produced code the
  grader's literal pass-signals (e.g. `toDataStreamResponse`) couldn't match.

### Entry 1 — Task 1: `/api/chat` Hono proxy + first `useChat`

- **Prompt:** Create `server/index.ts` + `server/api/chat.ts` (a `POST /api/chat`
  route that calls `streamText` against the W3 D4 Spring AI endpoint via
  `@ai-sdk/openai-compatible` and returns `result.toDataStreamResponse(...)` with
  the streaming-safe headers, forwarding `c.req.raw.signal` as `abortSignal`); add
  the Vite `/api/chat` proxy; build `TenantChatPanel` with
  `useChat({ api: '/api/chat', id })` rendering the transcript; mount the route.
- **Model output:** Produced the Hono route with `Content-Type: text/event-stream`,
  `Cache-Control: no-cache, no-transform`, `X-Accel-Buffering: no`, and
  `abortSignal: c.req.raw.signal`; a `@hono/node-server` entry on `:3001`; the
  Vite proxy; `TenantChatPanel` with the `chat-transcript` list and `data-role`
  per `<li>`; and the `/tenants/:id/chat` route under the protected layout.
  Added `@hono/node-server` (runtime) + `concurrently` (dev) and a `server`
  script so `pnpm dev` runs Vite + proxy together.
- **Verdict:** **Accepted.** The `abortSignal` forwarding (cancel the upstream
  LLM call when the browser aborts) was a suggestion I kept deliberately — see the
  reflection in the PR description.

### Entry 2 — Task 2: streaming UX polish (stop / regenerate / scroll / error)

- **Prompt:** Extend `TenantChatPanel` — pull `isLoading`/`stop`/`reload`/`error`;
  `role="status"` spinner while loading; Stop disabled when not loading,
  Regenerate disabled when loading, Send disabled when input is empty; auto-scroll
  on each `messages` change; `role="alert"` pane on error; map upstream 4xx/5xx
  into a clean error frame.
- **Model output:** Wired the three disabled rules (un-flipped), the
  `role="status"` / `role="alert"` panes, a `useEffect` + `endRef.scrollIntoView`
  keyed on `[messages]`, and a `getErrorMessage` mapper on `toDataStreamResponse`
  that converts an `APICallError` into a sentinel message (so the client's `error`
  field is set instead of the connection tearing).
- **Verdict:** **Accepted.** Chose the `getErrorMessage` sentinel-frame approach
  over a try/catch that would 500 the whole response and break the SSE contract.

### Entry 3 — Task 3: streamed tool calls + Zustand persistence

- **Prompt:** Create `server/api/chat-tools.ts` (`lookupTenant`, `nexusForState`
  as `zod`-typed `tool(...)`s hitting the REST backend); pass them into
  `streamText` with `maxSteps: 3`; build `ToolCallCard`
  (`aside aria-label="tool-call"`, `data-state`, `data-testid="tool-result"` on
  result); map `message.toolInvocations ?? []`; create `useTenantChatStore`
  (Zustand `persist`, `name: 'uc:tenant-chat'`) and call `appendAssistantMessage`
  **from `onFinish`, never per token**.
- **Model output:** Produced both tools, `maxSteps: 3`, the `ToolCallCard` with
  the result pane gated on `state === 'result'`, the inline tool-card mapping, and
  a `persist` store whose action is invoked **only** inside `onFinish`.
- **Verdict:** **Accepted, with one suggestion explicitly rejected** — see the
  reflection: persisting every token to Zustand "so the UI updates live" was
  declined per §9 (it corrupts persist rehydration); the store is written only on
  completion.

### Entry 4 — Task 4: MSW SSE handlers + Vitest tests

- **Prompt:** Create `src/test/sse-handlers.ts` (`http.post('/api/chat')`
  returning a hand-rolled `ReadableStream` of `0:"…"` text frames + a `d:{…}`
  finish frame, header `X-Vercel-AI-Data-Stream: v1`); spread into `handlers.ts`;
  add four test files asserting the named behaviors; target ≥ 20 new tests / ≥ 40
  total.
- **Model output:** Produced the SSE handler (plus a delayed variant so
  Stop-mid-stream is observable), the four test files (streamed-token render +
  `data-role="assistant"`; Stop → `status` off + truncated text; Regenerate →
  second POST; three `ToolCallCard` states; persist round-trip via
  `persist.rehydrate()`; upstream-500 → `role="alert"`), and a `setup.ts`
  `scrollIntoView` shim + an abort bridge scoped to `/api/chat` so `stop()` is
  testable under jsdom's AbortSignal. Suite reached **42 tests**.
- **Verdict:** **Accepted with edits.** Iterated the test infra empirically
  (jsdom AbortSignal vs the existing signal-stripping shim, per-frame delays for a
  deterministic Stop window) rather than trusting the reference handler verbatim,
  which closes the stream too fast to test Stop.

---

## Week 4 Day 5 — test pyramid, a11y budget, CI gate (2026-06-26)

Model for all W4 D5 entries: **Claude Opus 4.8** (`claude-opus-4-8`), via Claude Code.

### Entry 5 — Login page + tenant-list UI updates

- **Prompt:** Add a `LoginPage` that drives a stub credential form and persists a
  JWT to `localStorage` under `uc:jwt` so the protected layout unlocks; polish
  `TenantListPage` (accessible filter searchbox + row links); add the
  `docker-compose.dev.yml` + `application-local.yml` for a local backend.
- **Model output:** Produced `LoginPage` with a labelled email/password form and
  a `uc:jwt` write on submit, a `TenantListPage` whose filter is exposed as
  `role="searchbox"` (name `/filter/i`) ahead of the row `link`s in tab order,
  and the compose/profile config.
- **Verdict:** **Accepted.** Kept the labelled-field + accessible-name shape it
  proposed because every downstream RTL/e2e query (and the a11y tab-order test)
  binds to those roles rather than to markup.

### Entry 6 — RTL + Vitest harness (≥ 15 component tests)

- **Prompt:** Build `renderWithProviders` that mounts Apollo `MockedProvider` +
  TanStack `QueryClientProvider` + `MemoryRouter` and returns a single
  `userEvent.setup()`; add component contract tests; wire `vitest --coverage`
  and ESLint into the package; target ≥ 15 component tests.
- **Model output:** Produced the one-stop render helper (retry-off, `gcTime: 0`
  QueryClient so REST hooks never cache across tests; `route`/`apolloMocks`
  options; returns `{ user, queryClient, ...utils }`), plus `TenantListPage`
  contract tests and the Vitest/ESLint wiring.
- **Verdict:** **Accepted, with one suggestion rejected.** It first set up
  `userEvent` per-test inside each file; I had it return **one** `userEvent.setup()`
  from the helper instead — two setups desync keyboard state under jsdom (§9
  flake source). Queries go through `getByRole`/`getByLabelText`, not
  `getByTestId`.

### Entry 7 — MSW integration tests (≥ 12)

- **Prompt:** Add `TenantSummaryPage.integration.test.tsx` driving the real page +
  real QueryClient with MSW standing in for the Spring REST surface; cover happy
  path, error (500 → alert), loading, and empty/filter-narrowing branches; add
  the REST handlers; target ≥ 12 tests.
- **Model output:** Produced the integration suite that exercises the query hook,
  cache, and filter store for real (nothing stubbed past the network edge), using
  `server.use(tenantErrorHandler / tenantLoadingHandler)` to opt into branches and
  `resetHandlers` in `afterEach` to restore the happy path; added
  `tenantRestHandlers` (three rows so a filter test can narrow many → one → none).
- **Verdict:** **Accepted.** Chose per-case `server.use(...)` overrides over a
  separate handler stack per test file, so one happy-path definition stays the
  source of truth and branch tests state only their delta.

### Entry 8 — Playwright e2e happy-path

- **Prompt:** Add one capstone spec: signed-in engineer opens the tenant list →
  drills into a tenant → chats with the streamed assistant → confirms the reply +
  tool call survive a reload; mock every backend hop with `page.route`; add a
  `global-setup` that logs in once and persists `storageState`.
- **Model output:** Produced `tenant-chat.spec.ts` (Apollo + `/api/chat`
  SSE mocked at the browser edge, web-first `expect(...).toContainText`/`toHaveURL`
  assertions, an inline `AxeBuilder` scan asserting zero `wcag2a/2aa` violations),
  the `playwright.config.ts`, and `global-setup.ts` that drives the real login
  form once and writes `e2e/.auth/user.json` so no spec re-logs-in.
- **Verdict:** **Accepted, with one suggestion rejected.** It offered a
  `waitForTimeout(1000)` after the Send click "to be safe"; rejected per §9 —
  fixed sleeps flake when the stream is slow and waste time when it's fast. The
  spec waits on real conditions (streamed text in the `log`, URL change) via
  auto-retrying assertions instead. Queries are role/name-based throughout.

### Entry 9 — a11y budget + ESLint 9 + single CI `check` gate

- **Prompt:** Add keyboard/focus-order a11y tests beyond static role assertions,
  plus a `jest-axe` DOM audit; consolidate CI into one `pnpm run check`
  (tsc --noEmit && eslint . && vitest --coverage && playwright test) and run it
  from a `web-ci` workflow scoped to `multistate-web/**`.
- **Model output:** Produced `tenant.a11y.test.tsx` driving the `Tab` key to prove
  the filter-box-then-rows interactive order and a `jest-axe` `toHaveNoViolations`
  check on the summary table; `LoginPage`/`TenantSummaryPage`/`ErrorBoundary`
  contract tests; and the `web-ci.yml` that runs the single `check` script
  (installing Playwright Chromium first) and uploads the Playwright report.
- **Verdict:** **Accepted.** Kept the split it proposed — `axe` audits the static
  DOM, the Tab-driven tests cover the order a keyboard-only user actually
  experiences, which `axe` alone does not assert.
