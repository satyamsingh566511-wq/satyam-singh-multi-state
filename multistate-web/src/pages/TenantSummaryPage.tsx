// src/pages/TenantSummaryPage.tsx
//
// The tenant roster summary. Driven entirely by the TanStack REST hook
// (`/api/v1/tenants`) so the W4 D5 MSW integration suite can exercise the full
// happy / error / empty / filter matrix against a real query cache. The search
// box is wired to the shared Zustand filter store, so typing here narrows the
// visible rows the same way the detail-page FilterStrip does.
import type { ChangeEvent, ReactElement } from 'react';
import { useTenantsRest } from '../hooks/useGetMultiStateRest';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

export function TenantSummaryPage(): ReactElement {
  const { data, isPending, isError, error } = useTenantsRest();
  const searchText = useTenantFilterStore((s) => s.searchText);
  const setSearchText = useTenantFilterStore((s) => s.setSearchText);

  if (isPending) {
    return (
      <main className="page">
        <p role="status" className="message">
          Loading…
        </p>
      </main>
    );
  }

  if (isError) {
    return (
      <main className="page">
        <p role="alert" className="message">
          Failed to load tenants: {error.message}
        </p>
      </main>
    );
  }

  const needle = searchText.trim().toLowerCase();
  const rows =
    needle === ''
      ? data
      : data.filter(
          (t) =>
            t.name.toLowerCase().includes(needle) ||
            t.id.toLowerCase().includes(needle),
        );

  return (
    <main className="page">
      <div className="shell">
        <header className="list-head">
          <p className="tenant__eyebrow">Multi-State Tax Tracker</p>
          <h1 className="list-head__title">Tenant summary</h1>
        </header>

        <div className="field">
          <label className="field__label" htmlFor="summary-search">
            Filter tenants
          </label>
          <input
            id="summary-search"
            className="input"
            type="search"
            value={searchText}
            aria-label="Filter tenants"
            placeholder="name, id…"
            onChange={(e: ChangeEvent<HTMLInputElement>) =>
              setSearchText(e.currentTarget.value)
            }
          />
        </div>

        {rows.length === 0 ? (
          <p role="status" className="message empty-card">
            No results
          </p>
        ) : (
          <table className="summary-table">
            <caption className="sr-only">Tenant summary</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">ID</th>
                <th scope="col">Updated</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((tenant) => (
                <tr key={tenant.id}>
                  <td>{tenant.name}</td>
                  <td>{tenant.id}</td>
                  <td>{tenant.updatedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </main>
  );
}
