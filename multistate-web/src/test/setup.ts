// src/test/setup.ts
import '@testing-library/jest-dom';
import { afterEach, beforeAll, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import './server'; // installs the MSW server + its beforeAll/afterEach/afterAll hooks

// jsdom does not implement scrollIntoView; TenantChatPanel's auto-scroll effect
// calls it on every messages change. Provide a no-op so the effect never throws.
Element.prototype.scrollIntoView = vi.fn();

// jsdom clobbers the global AbortController with its own implementation, but
// Node's fetch (undici, which MSW drives) rejects any AbortSignal that isn't
// its native class — "Expected signal to be an instance of AbortSignal".
// Apollo's HttpLink always attaches such a signal, so we strip the signal
// before MSW sees the request. Registered after server.ts's beforeAll, so this
// wraps MSW's patched fetch.
//
// The streaming chat (`/api/chat`) DOES rely on cancellation: useChat.stop()
// aborts the request to halt a reply mid-stream. Since the real fetch can't
// carry the jsdom signal, we instead bridge it back in on the client side —
// the response body is wrapped so an abort errors the stream with AbortError,
// exactly as a torn network connection would. useChat then swallows the
// AbortError, freezes the partial message, and skips onFinish (so a Stopped
// partial is never persisted). Other endpoints keep the plain strip behaviour.
function urlOf(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input;
  if (input instanceof URL) return input.href;
  return input.url;
}

function bridgeAbort(
  body: ReadableStream<Uint8Array>,
  signal: AbortSignal,
): ReadableStream<Uint8Array> {
  const reader = body.getReader();
  return new ReadableStream<Uint8Array>({
    start(controller) {
      const onAbort = () => {
        void reader.cancel().catch(() => undefined);
        controller.error(
          new DOMException('The user aborted a request.', 'AbortError'),
        );
      };
      if (signal.aborted) {
        onAbort();
        return;
      }
      signal.addEventListener('abort', onAbort, { once: true });
      void (async () => {
        try {
          for (;;) {
            const { done, value } = await reader.read();
            if (done) {
              controller.close();
              break;
            }
            controller.enqueue(value);
          }
        } catch (err) {
          controller.error(err);
        } finally {
          signal.removeEventListener('abort', onAbort);
        }
      })();
    },
    cancel(reason) {
      void reader.cancel(reason).catch(() => undefined);
    },
  });
}

beforeAll(() => {
  const patchedFetch = globalThis.fetch;
  globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    if (!init?.signal) return patchedFetch(input, init);

    const { signal } = init;
    const rest = { ...init };
    delete rest.signal;

    if (!urlOf(input).includes('/api/chat')) {
      return patchedFetch(input, rest);
    }

    const response = await patchedFetch(input, rest);
    if (!response.body || signal.aborted) return response;
    return new Response(bridgeAbort(response.body, signal), {
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
    });
  };
});

// This jsdom/Node combo doesn't expose Web Storage, so components and the
// Apollo auth link that read `uc:jwt` would hit an undefined `localStorage`.
// Provide a minimal in-memory implementation for the whole test run.
class MemoryStorage implements Storage {
  private store = new Map<string, string>();
  get length(): number {
    return this.store.size;
  }
  clear(): void {
    this.store.clear();
  }
  getItem(key: string): string | null {
    return this.store.get(key) ?? null;
  }
  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }
  removeItem(key: string): void {
    this.store.delete(key);
  }
  setItem(key: string, value: string): void {
    this.store.set(key, value);
  }
}

const memoryStorage = new MemoryStorage();
Object.defineProperty(globalThis, 'localStorage', {
  value: memoryStorage,
  configurable: true,
  writable: true,
});

// jsdom is reused across tests in a file — unmount between cases so queries
// never see a stale component tree, and reset auth state between cases.
afterEach(() => {
  cleanup();
  localStorage.clear();
});
