// src/pages/TenantSummaryPage.integration.test.tsx
//
// Integration coverage: the real TenantSummaryPage wired to a real TanStack
// QueryClient, with MSW standing in for the Spring REST surface. Unlike the
// component contract file, nothing here is hand-stubbed past the network edge —
// the query hook, cache, and filter store all run for real. Tests opt into the
// error / loading / empty branches with server.use(...), which overrides one
// handler (not the whole stack); resetHandlers in afterEach restores the happy
// path between cases.
import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse, delay } from 'msw';
import { QueryClient } from '@tanstack/react-query';
import { server } from '../test/server';
import { tenantErrorHandler, tenantLoadingHandler } from '../test/handlers';
import { renderWithProviders } from '../test/renderWithProviders';
import { TenantSummaryPage } from './TenantSummaryPage';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

const ROUTE = '/tenants/ten_synth_a1b2/summary';

describe('TenantSummaryPage — integration via MSW', () => {
  beforeEach(() => {
    useTenantFilterStore.setState({ searchText: '' });
  });

  it('renders the first roster row in a table cell on the REST happy path', async () => {
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    expect(
      await screen.findByRole('cell', { name: /Stub Tenant 01/i }),
    ).toBeInTheDocument();
  });

  it('renders every tenant the endpoint returns', async () => {
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });
    expect(screen.getByRole('cell', { name: /Stub Tenant 02/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /Stub Tenant 03/i })).toBeInTheDocument();
  });

  it('renders the updatedAt value in its own cell', async () => {
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    expect(
      await screen.findByRole('cell', { name: '2025-01-01' }),
    ).toBeInTheDocument();
  });

  it('surfaces an alert when the REST endpoint returns 500', async () => {
    server.use(tenantErrorHandler);
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    expect(await screen.findByRole('alert')).toHaveTextContent(/failed/i);
  });

  it('keeps the error message specific (HTTP status leaks through)', async () => {
    server.use(tenantErrorHandler);
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    expect(await screen.findByRole('alert')).toHaveTextContent(/500/);
  });

  it('shows the role="status" skeleton before the data arrives', async () => {
    server.use(tenantLoadingHandler());
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
    // The table never paints while the request is in flight.
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('replaces the skeleton with the table once the request resolves', async () => {
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('renders the empty state when the roster is empty', async () => {
    server.use(http.get('/api/v1/tenants', () => HttpResponse.json([])));
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    const empty = await screen.findByText(/no results/i);
    expect(empty).toHaveAttribute('role', 'status');
  });

  it('populates the TanStack query cache on a successful load', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE, queryClient });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });
    expect(queryClient.getQueryData(['multistate', 'tenants'])).toHaveLength(3);
  });

  it('serves a second mount from cache with no loading flash', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const first = renderWithProviders(<TenantSummaryPage />, {
      route: ROUTE,
      queryClient,
    });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });
    first.unmount();

    // A fresh mount on the warm cache paints the cell synchronously — never the
    // role="status" skeleton.
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE, queryClient });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(
      screen.getByRole('cell', { name: /Stub Tenant 01/i }),
    ).toBeInTheDocument();
  });

  it('lets the engineer filter the table via userEvent.type', async () => {
    const { user } = renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });

    await user.type(
      screen.getByRole('searchbox', { name: /filter/i }),
      'Tenant 02',
    );

    expect(
      screen.queryByRole('cell', { name: /Stub Tenant 01/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('cell', { name: /Stub Tenant 02/i }),
    ).toBeInTheDocument();
  });

  it('filters by tenant id as well as name', async () => {
    const { user } = renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });

    await user.type(
      screen.getByRole('searchbox', { name: /filter/i }),
      'ten_synth_e5f6',
    );

    expect(
      screen.getByRole('cell', { name: /Stub Tenant 03/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('cell', { name: /Stub Tenant 01/i }),
    ).not.toBeInTheDocument();
  });

  it('shows the empty state when the filter matches nothing', async () => {
    const { user } = renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });

    await user.type(
      screen.getByRole('searchbox', { name: /filter/i }),
      'nomatch-xyz',
    );

    const empty = await screen.findByText(/no results/i);
    expect(empty).toHaveAttribute('role', 'status');
  });

  it('recovers to the happy path after a slow request resolves', async () => {
    server.use(
      http.get('/api/v1/tenants', async () => {
        await delay(15);
        return HttpResponse.json([
          { id: 'ten_late', name: 'Late Tenant', updatedAt: '2025-02-02' },
        ]);
      }),
    );
    renderWithProviders(<TenantSummaryPage />, { route: ROUTE });
    await waitFor(() =>
      expect(
        screen.getByRole('cell', { name: /Late Tenant/i }),
      ).toBeInTheDocument(),
    );
  });
});
