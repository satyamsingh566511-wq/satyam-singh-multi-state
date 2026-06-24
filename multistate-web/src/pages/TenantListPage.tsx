// src/pages/TenantListPage.tsx
import type { ReactElement } from 'react';
import { useLatestTenantsQuery } from '../gql/operations';

export function TenantListPage(): ReactElement {
  const { loading, error, data } = useLatestTenantsQuery();

  if (loading) {
    return (
      <p role="status" className="page">
        Loading…
      </p>
    );
  }

  if (error) {
    return (
      <p role="alert" className="page">
        Error: {error.message}
      </p>
    );
  }

  const rows = data?.latestTenants ?? [];
  if (rows.length === 0) {
    return <p className="page">No tenants yet.</p>;
  }

  return (
    <ul aria-label="tenant-list" className="page">
      {rows.map((tenant) => (
        <li key={tenant.id}>
          <a href={`/tenants/${tenant.id}`}>{tenant.name}</a>
        </li>
      ))}
    </ul>
  );
}
