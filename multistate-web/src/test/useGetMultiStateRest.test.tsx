// src/test/useGetMultiStateRest.test.tsx
import { describe, it, expect } from 'vitest';
import type { ReactElement, ReactNode } from 'react';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useGetMultiStateRest } from '../hooks/useGetMultiStateRest';

function createWrapper(): (props: { children: ReactNode }) => ReactElement {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }): ReactElement {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

describe('useGetMultiStateRest', () => {
  it('resolves the tenant from the REST endpoint via MSW', async () => {
    const { result } = renderHook(() => useGetMultiStateRest('stub-9'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({
      id: 'stub-9',
      name: 'stub tenant',
      updatedAt: '2025-01-04T00:00:00Z',
    });
  });

  it('does not fetch when the id is empty (enabled: false)', () => {
    const { result } = renderHook(() => useGetMultiStateRest(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.data).toBeUndefined();
  });
});
