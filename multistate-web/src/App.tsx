// src/App.tsx
//
// Hand-rolled hash router for W4 D1. TanStack Router lands on W4 D3; until then
// we match a single route off `window.location.hash` so the dev server can deep
// link to the tenant page without a router dependency.
import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { ErrorBoundary } from './components/ErrorBoundary';
import { TenantDetailPage } from './pages/TenantDetailPage';

const TENANT_ROUTE = '#/tenants/stub-id-1';

export function App(): ReactElement {
  const [hash, setHash] = useState<string>(window.location.hash);

  useEffect(() => {
    const onHashChange = (): void => setHash(window.location.hash);
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  if (hash === TENANT_ROUTE) {
    return (
      <ErrorBoundary
        fallback={(err, reset) => (
          <main className="page">
            <div role="alert" className="error-card">
              <h2>Something went wrong</h2>
              <pre>{err.message}</pre>
              <button type="button" className="btn btn--primary" onClick={reset}>
                Try again
              </button>
            </div>
          </main>
        )}
      >
        <TenantDetailPage />
      </ErrorBoundary>
    );
  }

  return (
    <p>
      Go to <a href={TENANT_ROUTE}>{TENANT_ROUTE}</a>
    </p>
  );
}
