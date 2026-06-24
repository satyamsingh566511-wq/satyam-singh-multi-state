// src/queryClient.ts
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000, // 1 minute — see W4 D3 §9 cache notes
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});
