// src/pages/TenantListPage.integration.test.tsx
//
// Integration coverage for the Apollo path: the real TenantListPage wired to a
// real ApolloClient + HttpLink + InMemoryCache, with MSW standing in for the
// GraphQL surface. The component-contract file (TenantListPage.test.tsx) hand-
// feeds every response through MockedProvider's inline mocks; this file lets the
// link, normalized cache, and Zustand filter store all run for real, and opts
// into the error / loading / empty branches with server.use(...) — overriding a
// single handler, restored by resetHandlers in afterEach (see test/server.ts).
//
// This closes the gap left by the REST integration suite: the same loading /
// error / cache / filter depth, now exercised against the LatestTenants query
// via latestTenantsLoadingHandler() and latestTenantsErrorHandler.
import { describe, it, expect, beforeEach } from 'vitest';
import type { ReactElement } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { graphql, HttpResponse, delay } from 'msw';
import { ApolloClient, InMemoryCache, HttpLink } from '@apollo/client';
import { ApolloProvider } from '@apollo/client/react';
import { MemoryRouter } from 'react-router-dom';
import { server } from '../test/server';
import {
  latestTenantsErrorHandler,
  latestTenantsLoadingHandler,
} from '../test/handlers';
import { TenantListPage } from './TenantListPage';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';
import { LatestTenantsDocument } from '../gql/generated/graphql';

// A fresh client per render — same uri as the production client (src/apollo/
// client.ts), which MSW's graphql.query handlers intercept by operation name
// regardless of endpoint. Mirrors prod's Tenant keyFields so the normalized
// cache assertions reflect real behaviour.
function makeClient(): ApolloClient {
  return new ApolloClient({
    link: new HttpLink({ uri: '/graphql' }),
    cache: new InMemoryCache({
      typePolicies: { Tenant: { keyFields: ['id'] } },
    }),
  });
}

function renderWithApollo(
  ui: ReactElement,
  client: ApolloClient = makeClient(),
) {
  const user = userEvent.setup();
  const utils = render(ui, {
    wrapper: ({ children }) => (
      <ApolloProvider client={client}>
        <MemoryRouter initialEntries={['/tenants']}>{children}</MemoryRouter>
      </ApolloProvider>
    ),
  });
  return { user, client, ...utils };
}

describe('TenantListPage — integration via MSW (real ApolloClient)', () => {
  beforeEach(() => {
    // The filter store is a module singleton; reset the search slice so a typed
    // filter from one test never leaks into the next.
    useTenantFilterStore.setState({ searchText: '' });
  });

  it('renders the first tenant row by accessible name on the happy path', async () => {
    renderWithApollo(<TenantListPage />);
    expect(
      await screen.findByRole('link', { name: /stub one/i }),
    ).toBeInTheDocument();
  });

  it('renders one list item per tenant the query returns', async () => {
    renderWithApollo(<TenantListPage />);
    await screen.findByRole('link', { name: /stub one/i });
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });

  it('renders the canonical list heading', async () => {
    renderWithApollo(<TenantListPage />);
    expect(
      await screen.findByRole('heading', { name: /tenants/i }),
    ).toBeInTheDocument();
  });

  it('surfaces a role="alert" when the query endpoint returns 500', async () => {
    server.use(latestTenantsErrorHandler);
    renderWithApollo(<TenantListPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/error/i);
  });

  it('shows the role="status" skeleton before the data arrives', () => {
    server.use(latestTenantsLoadingHandler());
    renderWithApollo(<TenantListPage />);
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
    // The tenant list never paints while the query is in flight.
    expect(screen.queryByRole('list', { name: 'tenant-list' })).not.toBeInTheDocument();
  });

  it('replaces the skeleton with rows once the query resolves', async () => {
    renderWithApollo(<TenantListPage />);
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
    await screen.findByRole('link', { name: /stub one/i });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('renders the role="status" empty state when no tenants come back', async () => {
    server.use(
      graphql.query('LatestTenants', () =>
        HttpResponse.json({ data: { latestTenants: [] } }),
      ),
    );
    renderWithApollo(<TenantListPage />);
    const empty = await screen.findByText(/no results/i);
    expect(empty).toHaveAttribute('role', 'status');
  });

  it('populates the Apollo normalized cache on a successful load', async () => {
    const { client } = renderWithApollo(<TenantListPage />);
    await screen.findByRole('link', { name: /stub one/i });
    const cached = client.readQuery({ query: LatestTenantsDocument });
    expect(cached?.latestTenants).toHaveLength(3);
  });

  it('serves a second mount from cache with no loading flash', async () => {
    const client = makeClient();
    const first = renderWithApollo(<TenantListPage />, client);
    await screen.findByRole('link', { name: /stub one/i });
    first.unmount();

    // cache-first is the Apollo default: a fresh mount on the warm cache paints
    // the row synchronously — never the role="status" skeleton.
    renderWithApollo(<TenantListPage />, client);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: /stub one/i }),
    ).toBeInTheDocument();
  });

  it('narrows the visible rows as the engineer types into the filter box', async () => {
    const { user } = renderWithApollo(<TenantListPage />);
    await screen.findByRole('link', { name: /stub one/i });

    await user.type(screen.getByRole('searchbox', { name: /filter/i }), 'two');

    expect(
      screen.queryByRole('link', { name: /stub one/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: /stub two/i }),
    ).toBeInTheDocument();
  });

  it('matches the filter against the tenant id, not just the name', async () => {
    const { user } = renderWithApollo(<TenantListPage />);
    await screen.findByRole('link', { name: /stub one/i });

    await user.type(screen.getByRole('searchbox', { name: /filter/i }), 'stub-3');

    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(
      screen.getByRole('link', { name: /stub three/i }),
    ).toBeInTheDocument();
  });

  it('shows the empty state when the filter matches nothing', async () => {
    const { user } = renderWithApollo(<TenantListPage />);
    await screen.findByRole('link', { name: /stub one/i });

    await user.type(
      screen.getByRole('searchbox', { name: /filter/i }),
      'zzz-no-match',
    );

    const empty = await screen.findByText(/no results/i);
    expect(empty).toHaveAttribute('role', 'status');
  });

  it('recovers to the happy path after a slow query resolves', async () => {
    server.use(
      graphql.query('LatestTenants', async () => {
        await delay(15);
        return HttpResponse.json({
          data: {
            latestTenants: [
              {
                id: 'late-1',
                name: 'Late Tenant',
                updatedAt: '2025-02-02T00:00:00Z',
                __typename: 'Tenant',
              },
            ],
          },
        });
      }),
    );
    renderWithApollo(<TenantListPage />);
    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: /Late Tenant/i }),
      ).toBeInTheDocument(),
    );
  });
});
