// src/pages/TenantDetailPage.tsx
import { useEffect, useReducer, useState } from 'react';
import type { ReactElement } from 'react';
import type { Tenant } from '../types/tenant';
import { FilterStrip } from '../components/FilterStrip';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';
import { useDebouncedSearch } from '../hooks/useDebouncedSearch';
import { detailReducer, INITIAL_DETAIL_STATE } from './TenantDetailPage.reducer';

export function TenantDetailPage(): ReactElement {
  // The page is a small state machine now: idle → loading → success|empty|error.
  // The reducer owns every transition; this component only dispatches and reads
  // `state.status`, and TypeScript narrows each render branch automatically.
  const [state, dispatch] = useReducer(detailReducer, INITIAL_DETAIL_STATE);

  // Debounced mirror of the store's `searchText` — lags raw keystrokes by 300ms
  // so the "filtering for" indicator below doesn't thrash on every character.
  const debouncedSearch = useDebouncedSearch();

  // DEV-only error-boundary probe. Error boundaries catch errors thrown during
  // render, NOT in event handlers — so the button flips this flag and the throw
  // happens on the next render. The boundary then unmounts this subtree; its
  // retry re-mounts us fresh (flag back to false).
  const [boom, setBoom] = useState(false);
  if (boom) {
    throw new Error('Intentional error from the “Trigger error” dev button.');
  }

  useEffect(() => {
    let cancelled = false;
    dispatch({ type: 'fetch/start' });
    fetch('/mocks/tenant.json')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<Tenant | null>;
      })
      .then((payload) => {
        // A null payload (entity not found by the stub fetcher) becomes `empty`.
        if (!cancelled) dispatch({ type: 'fetch/success', payload });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const message = err instanceof Error ? err.message : String(err);
        dispatch({ type: 'fetch/error', error: message });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === 'idle' || state.status === 'loading') {
    return (
      <main className="page">
        <p className="message">Loading…</p>
      </main>
    );
  }
  if (state.status === 'error') {
    return (
      <main className="page">
        <p className="message" role="alert">
          Failed to load: {state.error}
        </p>
      </main>
    );
  }
  if (state.status === 'empty') {
    return (
      <main className="page">
        <p className="message">Not found.</p>
      </main>
    );
  }

  // `state.status` is narrowed to 'success' here, so `state.data` is a Tenant.
  const { data } = state;

  return (
    <main className="page">
      <div className="shell">
        {/* Filters live above the detail card; each control owns its own slice. */}
        <FilterStrip debouncedSearch={debouncedSearch} />

        <section className="card">
          <p className="tenant__eyebrow">Tenant</p>
          <h1 className="tenant__title">{data.id}</h1>

          <dl className="detail">
            <dt>primaryState</dt>
            <dd>{data.primaryState}</dd>
            <dt>stateCount</dt>
            <dd>{data.stateCount}</dd>
            <dt>totalAllocation</dt>
            <dd>{data.totalAllocation}</dd>
          </dl>

          {/* Both read the same `threshold` slice — no prop threading. */}
          <div className="threshold">
            <span className="field__label">Threshold</span>
            <ThresholdSlider />
            <ThresholdReadout />
          </div>

          {import.meta.env.DEV && (
            <button
              type="button"
              className="btn btn--ghost trigger-error"
              onClick={() => setBoom(true)}
            >
              Trigger error
            </button>
          )}
        </section>
      </div>
    </main>
  );
}
