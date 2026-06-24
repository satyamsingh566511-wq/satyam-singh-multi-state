// src/test/setup.ts
import '@testing-library/jest-dom';
import { afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import './server'; // installs the MSW server + its beforeAll/afterEach/afterAll hooks

// jsdom clobbers the global AbortController with its own implementation, but
// Node's fetch (undici, which MSW drives) rejects any AbortSignal that isn't
// its native class — "Expected signal to be an instance of AbortSignal".
// Apollo's HttpLink always attaches such a signal. Nothing under test relies
// on request cancellation, so strip the signal before MSW sees the request.
// Registered after server.ts's beforeAll, so this wraps MSW's patched fetch.
beforeAll(() => {
  const patchedFetch = globalThis.fetch;
  globalThis.fetch = (input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.signal) {
      const rest = { ...init };
      delete rest.signal;
      return patchedFetch(input, rest);
    }
    return patchedFetch(input, init);
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
