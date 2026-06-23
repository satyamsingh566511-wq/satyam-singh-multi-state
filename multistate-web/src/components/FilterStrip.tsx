// src/components/FilterStrip.tsx
import type { ChangeEvent, KeyboardEvent, ReactElement } from 'react';
import { useState } from 'react';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

type Props = {
  // The debounced search value, lifted from the page so the "filtering for"
  // hint can sit right under the search box. It lags the raw store field.
  readonly debouncedSearch: string;
};

// Each control below subscribes to ONLY its own slice of the store. A keystroke
// in the search box re-renders SearchControl alone — it never re-renders the
// chip list, the checkbox, or the date display. That selective subscription is
// the whole point of reading slices instead of the whole store object.

function SearchControl({ debouncedSearch }: Props): ReactElement {
  const searchText = useTenantFilterStore((s) => s.searchText);
  const setSearchText = useTenantFilterStore((s) => s.setSearchText);

  return (
    <div className="field">
      <label className="field__label" htmlFor="filter-search">
        Search
      </label>
      <input
        id="filter-search"
        className="input"
        type="search"
        value={searchText}
        aria-label="Search tenants"
        placeholder="name, id…"
        onChange={(e: ChangeEvent<HTMLInputElement>) =>
          setSearchText(e.currentTarget.value)
        }
      />
      {debouncedSearch !== '' && (
        <p className="filtering" aria-live="polite">
          filtering for: <b>‘{debouncedSearch}’</b>
        </p>
      )}
    </div>
  );
}

function StateFilterControl(): ReactElement {
  const stateFilter = useTenantFilterStore((s) => s.stateFilter);
  const setStateFilter = useTenantFilterStore((s) => s.setStateFilter);
  const [draft, setDraft] = useState('');

  const add = (): void => {
    const code = draft.trim().toUpperCase();
    if (code === '' || stateFilter.includes(code)) return;
    setStateFilter([...stateFilter, code]);
    setDraft('');
  };

  const remove = (code: string): void =>
    setStateFilter(stateFilter.filter((c) => c !== code));

  return (
    <fieldset className="states">
      <legend>States</legend>
      <ul className="chips">
        {stateFilter.map((code) => (
          <li key={code}>
            <button
              type="button"
              className="chip"
              aria-label={`Remove ${code}`}
              onClick={() => remove(code)}
            >
              {code} ✕
            </button>
          </li>
        ))}
      </ul>
      <div className="row">
        <input
          className="input"
          value={draft}
          aria-label="Add state filter"
          placeholder="CA"
          onChange={(e) => setDraft(e.currentTarget.value)}
          onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              add();
            }
          }}
        />
        <button type="button" className="btn" onClick={add}>
          Add
        </button>
      </div>
    </fieldset>
  );
}

function ArchivedControl(): ReactElement {
  const includeArchived = useTenantFilterStore((s) => s.includeArchived);
  const setIncludeArchived = useTenantFilterStore((s) => s.setIncludeArchived);

  return (
    <label className="check">
      <input
        type="checkbox"
        checked={includeArchived}
        onChange={(e) => setIncludeArchived(e.currentTarget.checked)}
      />
      Include archived
    </label>
  );
}

function DateRangeControl(): ReactElement {
  // The reference store ships no `setDateRange` action, so the range is shown
  // read-only — it's session state seeded by INITIAL, not yet user-editable.
  const [from, to] = useTenantFilterStore((s) => s.dateRange);

  return (
    <span className="daterange" aria-label="Date range">
      Range: {from || '—'} → {to ?? '—'}
    </span>
  );
}

function ResetControl(): ReactElement {
  const reset = useTenantFilterStore((s) => s.reset);

  return (
    <button type="button" className="btn btn--ghost" onClick={reset}>
      Reset filters
    </button>
  );
}

export function FilterStrip({ debouncedSearch }: Props): ReactElement {
  return (
    <section className="card filters" aria-label="Filters">
      <p className="filters__title">Filters</p>
      <SearchControl debouncedSearch={debouncedSearch} />
      <StateFilterControl />
      <ArchivedControl />
      <DateRangeControl />
      <div className="filters__foot">
        <ResetControl />
      </div>
    </section>
  );
}
