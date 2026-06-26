// src/pages/tenant.a11y.test.tsx
//
// Keyboard / focus-order accessibility checks that role assertions alone don't
// cover. axe (wired into the component-contract files) audits the static DOM;
// these drive the Tab key to prove the interactive order a screen-reader or
// keyboard-only user actually experiences.
import { describe, it, expect, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { axe } from 'jest-axe';
import type { MockedResponse } from '@apollo/client/testing';
import { renderWithProviders } from '../test/renderWithProviders';
import { TenantListPage } from './TenantListPage';
import { TenantSummaryPage } from './TenantSummaryPage';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';
import { LatestTenantsDocument } from '../gql/generated/graphql';

const threeRows: MockedResponse = {
  request: { query: LatestTenantsDocument },
  result: {
    data: {
      latestTenants: [
        { id: 'stub-1', name: 'stub one', updatedAt: '2025-01-01T00:00:00Z' },
        { id: 'stub-2', name: 'stub two', updatedAt: '2025-01-02T00:00:00Z' },
      ],
    },
  },
};

describe('tenant pages — keyboard accessibility', () => {
  beforeEach(() => {
    useTenantFilterStore.setState({ searchText: '' });
  });

  it('puts the filter box first in the list page tab order, then the rows', async () => {
    const { user } = renderWithProviders(<TenantListPage />, {
      apolloMocks: [threeRows],
    });
    await screen.findByRole('link', { name: /stub one/i });

    await user.tab();
    expect(screen.getByRole('searchbox', { name: /filter/i })).toHaveFocus();

    await user.tab();
    expect(screen.getByRole('link', { name: /stub one/i })).toHaveFocus();

    await user.tab();
    expect(screen.getByRole('link', { name: /stub two/i })).toHaveFocus();
  });

  it('lets the engineer type into the list filter straight from the keyboard', async () => {
    const { user } = renderWithProviders(<TenantListPage />, {
      apolloMocks: [threeRows],
    });
    await screen.findByRole('link', { name: /stub one/i });

    await user.tab();
    await user.keyboard('two');

    expect(screen.getByRole('searchbox', { name: /filter/i })).toHaveValue('two');
    expect(
      screen.queryByRole('link', { name: /stub one/i }),
    ).not.toBeInTheDocument();
  });

  it('reaches the summary filter box by keyboard and exposes a label', async () => {
    const { user } = renderWithProviders(<TenantSummaryPage />, {
      route: '/tenants/x/summary',
    });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });

    await user.tab();
    expect(screen.getByRole('searchbox', { name: /filter/i })).toHaveFocus();
  });

  it('summary table itself carries no axe violations', async () => {
    const { container } = renderWithProviders(<TenantSummaryPage />, {
      route: '/tenants/x/summary',
    });
    await screen.findByRole('cell', { name: /Stub Tenant 01/i });
    expect(await axe(container)).toHaveNoViolations();
  });
});
