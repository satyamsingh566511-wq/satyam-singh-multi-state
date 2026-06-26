// src/pages/TenantSummaryPage.test.tsx
//
// Component contract for the REST-backed tenant roster. The default MSW handler
// (handlers.ts → tenantRestHandlers) supplies the happy payload; individual
// tests opt into the error / loading / empty branches with server.use(...).
import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { axe } from 'jest-axe';
import { server } from '../test/server';
import {
  tenantErrorHandler,
  tenantLoadingHandler,
} from '../test/handlers';
import { renderWithProviders } from '../test/renderWithProviders';
import { TenantSummaryPage } from './TenantSummaryPage';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

describe('TenantSummaryPage — component contract', () => {
  beforeEach(() => {
    useTenantFilterStore.setState({ searchText: '' });
  });

  it('renders the summary heading', async () => {
    renderWithProviders(<TenantSummaryPage />, { route: '/tenants/x/summary' });
    expect(
      await screen.findByRole('heading', { name: /tenant summary/i }),
    ).toBeInTheDocument();
  });

  it('paints a role="status" skeleton before the data resolves', () => {
    server.use(tenantLoadingHandler());
    renderWithProviders(<TenantSummaryPage />, { route: '/tenants/x/summary' });
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
  });

  it('shows the first tenant in a table cell once the REST hook resolves', async () => {
    renderWithProviders(<TenantSummaryPage />, { route: '/tenants/x/summary' });
    expect(
      await screen.findByRole('cell', { name: /Stub Tenant 01/i }),
    ).toBeInTheDocument();
  });

  it('surfaces a role="alert" when the REST endpoint returns 500', async () => {
    server.use(tenantErrorHandler);
    renderWithProviders(<TenantSummaryPage />, { route: '/tenants/x/summary' });
    expect(await screen.findByRole('alert')).toHaveTextContent(/failed/i);
  });

  it('renders a role="status" empty state when the list is empty', async () => {
    server.use(http.get('/api/v1/tenants', () => HttpResponse.json([])));
    renderWithProviders(<TenantSummaryPage />, { route: '/tenants/x/summary' });
    const empty = await screen.findByText(/no results/i);
    expect(empty).toHaveAttribute('role', 'status');
  });

  it('narrows the visible cells as the engineer types into the filter box', async () => {
    const { user } = renderWithProviders(<TenantSummaryPage />, {
      route: '/tenants/x/summary',
    });
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

  it('has no axe-detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<TenantSummaryPage />, {
      route: '/tenants/x/summary',
    });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });
    await waitFor(async () => {
      expect(await axe(container)).toHaveNoViolations();
    });
  });
});
