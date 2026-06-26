// src/test/renderWithProviders.tsx
//
// One render helper that mounts every provider a page needs: Apollo's
// MockedProvider (so GraphQL hooks resolve from inline mocks), TanStack's
// QueryClientProvider (retry off + gcTime 0 so REST hooks never cache across
// tests), and a MemoryRouter seeded with `initialEntries`. It returns the RTL
// utils plus a single `userEvent.setup()` instance and the QueryClient, so a
// test never sets up userEvent twice (which desyncs keyboard state).
import type { ReactElement } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MockedProvider } from '@apollo/client/testing/react';
import type { MockedResponse } from '@apollo/client/testing';

interface ProviderOptions {
  /** Initial history entry for the MemoryRouter (defaults to the list route). */
  readonly route?: string;
  /** Apollo mocked responses for any GraphQL hook the page fires on mount. */
  readonly apolloMocks?: readonly MockedResponse[];
  /** Override the QueryClient (otherwise a fresh, retry-free one per render). */
  readonly queryClient?: QueryClient;
}

export function renderWithProviders(
  ui: ReactElement,
  opts: ProviderOptions & Omit<RenderOptions, 'wrapper'> = {},
) {
  const {
    route = '/tenants',
    apolloMocks = [],
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 } },
    }),
    ...rtl
  } = opts;

  const user = userEvent.setup();
  const utils = render(ui, {
    wrapper: ({ children }) => (
      <MockedProvider mocks={[...apolloMocks]}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
        </QueryClientProvider>
      </MockedProvider>
    ),
    ...rtl,
  });

  return { user, queryClient, ...utils };
}
