// src/pages/TenantListPage.tsx
import type { ChangeEvent, ReactElement } from 'react';
import { useLatestTenantsQuery } from '../gql/operations';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

/** First letters of up to two words, for the row avatar. */
function initials(name: string): string {
  const letters = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('');
  return letters || '?';
}

/** Render an ISO timestamp as a short, locale-aware date; pass through if unparseable. */
function formatUpdated(iso: string): string {
  const when = new Date(iso);
  if (Number.isNaN(when.getTime())) return iso;
  return when.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function TenantListPage(): ReactElement {
  const { loading, error, data } = useLatestTenantsQuery();
  const searchText = useTenantFilterStore((s) => s.searchText);
  const setSearchText = useTenantFilterStore((s) => s.setSearchText);

  if (loading) {
    return (
      <main className="page">
        <p role="status" className="message">
          Loading…
        </p>
      </main>
    );
  }

  if (error) {
    return (
      <main className="page">
        <p role="alert" className="message">
          Error: {error.message}
        </p>
      </main>
    );
  }

  const all = data?.latestTenants ?? [];
  const needle = searchText.trim().toLowerCase();
  const rows =
    needle === ''
      ? all
      : all.filter(
          (t) =>
            t.name.toLowerCase().includes(needle) ||
            t.id.toLowerCase().includes(needle),
        );

  return (
    <main className="page">
      <div className="shell">
        <header className="list-head">
          <p className="tenant__eyebrow">Multi-State Tax Tracker</p>
          <h1 className="list-head__title">Tenants</h1>
          <p className="list-head__sub">
            {rows.length} {rows.length === 1 ? 'tenant' : 'tenants'} tracked
          </p>
        </header>

        <div className="field">
          <label className="field__label" htmlFor="tenant-search">
            Filter tenants
          </label>
          <input
            id="tenant-search"
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
          <ul aria-label="tenant-list" className="tenant-list">
            {rows.map((tenant) => (
              <li key={tenant.id} className="tenant-list__item">
                <a className="tenant-row" href={`/tenants/${tenant.id}`}>
                  <span className="tenant-row__avatar" aria-hidden="true">
                    {initials(tenant.name)}
                  </span>
                  <span className="tenant-row__body">
                    <span className="tenant-row__name">{tenant.name}</span>
                    <span className="tenant-row__meta">
                      {tenant.id}
                      {tenant.updatedAt
                        ? ` · updated ${formatUpdated(tenant.updatedAt)}`
                        : ''}
                    </span>
                  </span>
                  <span className="tenant-row__chevron" aria-hidden="true">
                    →
                  </span>
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
