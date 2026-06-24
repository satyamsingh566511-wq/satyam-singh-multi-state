// src/test/TenantListPage.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { ApolloProvider } from '@apollo/client/react';
import { apolloClient } from '../apollo/client';
import { TenantListPage } from '../pages/TenantListPage';

describe('TenantListPage', () => {
  it('renders three rows once the MSW handler resolves', async () => {
    render(
      <ApolloProvider client={apolloClient}>
        <TenantListPage />
      </ApolloProvider>,
    );

    await waitFor(() =>
      expect(screen.getAllByRole('listitem')).toHaveLength(3),
    );
    expect(screen.getByText('stub one')).toBeInTheDocument();
  });
});
