// src/test/TenantSummaryPage.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { ApolloProvider } from '@apollo/client/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { apolloClient } from '../apollo/client';
import { TenantSummaryPage } from '../pages/TenantSummaryPage';

describe('TenantSummaryPage', () => {
  it('paints the optimistic placeholder, then swaps in the server value', async () => {
    render(
      <ApolloProvider client={apolloClient}>
        <MemoryRouter initialEntries={['/tenants/stub-9/summary']}>
          <Routes>
            <Route
              path="/tenants/:id/summary"
              element={<TenantSummaryPage />}
            />
          </Routes>
        </MemoryRouter>
      </ApolloProvider>,
    );

    // fireEvent is synchronous: React flushes the loading state before the
    // MSW handler (delayed) resolves, so the placeholder is observable.
    fireEvent.click(screen.getByRole('button', { name: /summarize/i }));
    expect(screen.getByText(/thinking/)).toBeInTheDocument();

    // The MSW handler resolves and the real value replaces the placeholder.
    await waitFor(() =>
      expect(screen.getByText('stub summary from MSW')).toBeInTheDocument(),
    );
    expect(screen.queryByText(/thinking/)).not.toBeInTheDocument();
  });
});
