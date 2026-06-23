// src/stores/useTenantFilterStore.ts
import { create } from 'zustand';
import { createJSONStorage, devtools, persist } from 'zustand/middleware';
import type { StateStorage } from 'zustand/middleware';

// Real `localStorage` in the browser (so a reload rehydrates `threshold`), but
// an in-memory no-op fallback when it is absent (jsdom/Node test runs, SSR) so
// a `set()` never throws on `storage.setItem` of an undefined storage.
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

type FilterState = {
    readonly stateFilter:    ReadonlyArray<string>;
    readonly dateRange:          readonly [string, string | null];
    readonly searchText:         string;
    readonly includeArchived:    boolean;
    readonly threshold:          number;
};

type FilterActions = {
    readonly setStateFilter: (next: ReadonlyArray<string>) => void;
    readonly setSearchText:          (next: string)                => void;
    readonly setThreshold:           (next: number)                => void;
    readonly setIncludeArchived:     (next: boolean)               => void;
    readonly reset:                  ()                            => void;
};

const INITIAL: FilterState = {
    stateFilter: [],
    dateRange:       ['', null],
    searchText:      '',
    includeArchived: false,
    threshold:       50,
};

// Only `threshold` is persisted across reloads — see partialize below.
// Search text persisted across reloads would be a UX bug (engineer types
// "foo" for an unrelated reason, returns next week, sees results still
// filtered by "foo"); the filter chips and date range are session state.
export const useTenantFilterStore = create<FilterState & FilterActions>()(
    devtools(
        persist(
            (set) => ({
                ...INITIAL,
                setStateFilter: (next) =>
                    set({ stateFilter: next }, false, 'filters/setStateFilter'),
                setSearchText: (next) =>
                    set({ searchText: next }, false, 'filters/setSearchText'),
                setThreshold: (next) =>
                    set({ threshold: next }, false, 'filters/setThreshold'),
                setIncludeArchived: (next) =>
                    set({ includeArchived: next }, false, 'filters/setIncludeArchived'),
                reset: () => set(INITIAL, false, 'filters/reset'),
            }),
            {
                name: 'multistate-web:filters',
                storage: createJSONStorage(resolveStorage),
                // partialize: only `threshold` survives a reload.
                partialize: (s) => ({ threshold: s.threshold }),
            },
        ),
        { name: 'useTenantFilterStore' },
    ),
);