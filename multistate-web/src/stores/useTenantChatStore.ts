// src/stores/useTenantChatStore.ts
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { StateStorage } from 'zustand/middleware';
import type { Message } from 'ai';

// Real `localStorage` in the browser (so a reload rehydrates the transcript),
// but an in-memory no-op fallback when it is absent (SSR) so a `set()` never
// throws on an undefined storage. Mirrors useTenantFilterStore's approach.
function resolveStorage(): StateStorage {
  try {
    if (typeof localStorage !== 'undefined') return localStorage;
  } catch {
    // Access can throw in sandboxed/locked-down contexts — fall through.
  }
  const mem = new Map<string, string>();
  return {
    getItem: (name) => mem.get(name) ?? null,
    setItem: (name, value) => void mem.set(name, value),
    removeItem: (name) => void mem.delete(name),
  };
}

interface TenantChatStoreState {
  readonly messages: readonly Message[];
  readonly appendAssistantMessage: (m: Message) => void;
  readonly clear: () => void;
}

// Persists the *completed* transcript only. appendAssistantMessage is called
// from useChat's onFinish (never per-token, never mid-stream) so the persist
// middleware only ever serialises whole messages — see §9. An in-progress
// partial message that was Stopped is therefore absent after a reload.
export const useTenantChatStore = create<TenantChatStoreState>()(
  persist(
    (set) => ({
      messages: [],
      appendAssistantMessage: (m) =>
        set((s) => ({ messages: [...s.messages, m] })),
      clear: () => set({ messages: [] }),
    }),
    {
      name: 'uc:tenant-chat',
      storage: createJSONStorage(resolveStorage),
    },
  ),
);
