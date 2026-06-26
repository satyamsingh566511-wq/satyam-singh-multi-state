// src/pages/TenantListPage.test.tsx
//
// Component contract for the Apollo-backed tenant list. Every GraphQL response
// is supplied inline through MockedProvider (see renderWithProviders), so these
// tests never touch the network — they pin the render branches: heading,
// happy-path rows, the polite loading skeleton, the empty state, the error
// alert, and the Zustand-filter-store wiring.
import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { axe } from 'jest-axe';
import type { MockedResponse } from '@apollo/client/testing';
import { renderWithProviders } from '../test/renderWithProviders';
import { TenantListPage } from './TenantListPage';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';
import { LatestTenantsDocument } from '../gql/generated/graphql';

const threeRows: MockedResponse = {
  request: { query: LatestTenantsDocument },
  result: {
    data: {
      latestTenants: [
        { id: 'stub-1', name: 'stub one', updatedAt: '2025-01-01T00:00:00Z' },
        { id: 'stub-2', name: 'stub two', updatedAt: '2025-01-02T00:00:00Z' },
        { id: 'stub-3', name: 'stub three', updatedAt: '2025-01-03T00:00:00Z' },
      ],
    },
  },
};

const emptyRows: MockedResponse = {
  request: { query: LatestTenantsDocument },
  result: { data: { latestTenants: [] } },
};

const erroring: MockedResponse = {
  request: { query: LatestTenantsDocument },
  error: new Error('GraphQL transport exploded'),
};

describe('TenantListPage — component contract', () => {
  beforeEach(() => {
    // The filter store is a module singleton; reset the search slice so a typed
    // filter from one test never leaks into the next.
    useTenantFilterStore.setState({ searchText: '' });
  });

  it('renders the list heading with the canonical accessible name', async () => {
    renderWithProviders(<TenantListPage />, { apolloMocks: [threeRows] });
    expect(
      await screen.findByRole('heading', { name: /tenants/i }),
    ).toBeInTheDocument();
  });

  it('shows the first row by accessible name once the mock resolves', async () => {
    renderWithProviders(<TenantListPage />, { apolloMocks: [threeRows] });
    expect(
      await screen.findByRole('link', { name: /stub one/i }),
    ).toBeInTheDocument();
  });

  it('renders one list item per returned tenant', async () => {
    renderWithProviders(<TenantListPage />, { apolloMocks: [threeRows] });
    await screen.findByRole('link', { name: /stub one/i });
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });

  it('renders a polite role="status" skeleton while loading', () => {
    renderWithProviders(<TenantListPage />, { apolloMocks: [threeRows] });
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
  });

  it('renders a role="status" empty state when no tenants come back', async () => {
    renderWithProviders(<TenantListPage />, { apolloMocks: [emptyRows] });
    const empty = await screen.findByText(/no results/i);
    expect(empty).toHaveAttribute('role', 'status');
  });

  it('renders a role="alert" with the error message when the query fails', async () => {
    renderWithProviders(<TenantListPage />, { apolloMocks: [erroring] });
    expect(await screen.findByRole('alert')).toHaveTextContent(/error/i);
  });

  it('narrows the visible rows as the engineer types into the filter box', async () => {
    const { user } = renderWithProviders(<TenantListPage />, {
      apolloMocks: [threeRows],
    });
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
    const { user } = renderWithProviders(<TenantListPage />, {
      apolloMocks: [threeRows],
    });
    await screen.findByRole('link', { name: /stub one/i });

    await user.type(screen.getByRole('searchbox', { name: /filter/i }), 'stub-3');

    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(
      screen.getByRole('link', { name: /stub three/i }),
    ).toBeInTheDocument();
  });

  it('shows the empty state when the filter matches nothing', async () => {
    const { user } = renderWithProviders(<TenantListPage />, {
      apolloMocks: [threeRows],
    });
    await screen.findByRole('link', { name: /stub one/i });

    await user.type(
      screen.getByRole('searchbox', { name: /filter/i }),
      'zzz-no-match',
    );

    const empty = await screen.findByText(/no results/i);
    expect(empty).toHaveAttribute('role', 'status');
  });

  it('has no axe-detectable accessibility violations', async () => {
    const { container } = renderWithProviders(<TenantListPage />, {
      apolloMocks: [threeRows],
    });
    await screen.findByRole('link', { name: /stub one/i });
    await waitFor(async () => {
      expect(await axe(container)).toHaveNoViolations();
    });
  });
});
