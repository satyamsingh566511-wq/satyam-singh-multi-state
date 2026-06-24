// src/test/handlers.ts
import { graphql, http, HttpResponse, delay } from 'msw';

export const handlers = [
  graphql.query('LatestTenants', () =>
    HttpResponse.json({
      data: {
        latestTenants: [
          { id: 'stub-1', name: 'stub one', updatedAt: '2025-01-01T00:00:00Z', __typename: 'Tenant' },
          { id: 'stub-2', name: 'stub two', updatedAt: '2025-01-02T00:00:00Z', __typename: 'Tenant' },
          { id: 'stub-3', name: 'stub three', updatedAt: '2025-01-03T00:00:00Z', __typename: 'Tenant' },
        ],
      },
    }),
  ),
  graphql.mutation('SummarizeTenant', async ({ variables }) => {
    // A small delay keeps the optimistic placeholder observable before the
    // real server value swaps in — see TenantSummaryPage.test.tsx.
    await delay(20);
    return HttpResponse.json({
      data: {
        summarizeTenant: {
          __typename: 'TenantSummary',
          id: String(variables.id),
          summaryText: 'stub summary from MSW',
          confidence: 'HIGH',
        },
      },
    });
  }),
  http.get('http://localhost:8080/api/v1/tenants/:id', ({ params }) =>
    HttpResponse.json({
      id: String(params.id),
      name: 'stub tenant',
      updatedAt: '2025-01-04T00:00:00Z',
    }),
  ),
];
