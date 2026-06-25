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
