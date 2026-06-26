// src/test/handlers.ts
import { graphql, http, HttpResponse, delay } from 'msw';
import { sseHandlers } from './sse-handlers';

// The default REST list payload the TenantSummaryPage roster renders. Three
// rows so a filter test can narrow from many → one → none. Declared before the
// aggregate `handlers` array below, which spreads it in.
export const tenantRestHandlers = [
  http.get('/api/v1/tenants', () =>
    HttpResponse.json([
      { id: 'ten_synth_a1b2', name: 'Stub Tenant 01', updatedAt: '2025-01-01' },
      { id: 'ten_synth_c3d4', name: 'Stub Tenant 02', updatedAt: '2025-01-02' },
      { id: 'ten_synth_e5f6', name: 'Stub Tenant 03', updatedAt: '2025-01-03' },
    ]),
  ),
];

export const handlers = [
  // W4 D4 streaming chat (/api/chat). Spread first so the more specific SSE
  // route is considered before any catch-alls below.
  ...sseHandlers,
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
  // ---- TanStack REST list happy path (TenantSummaryPage roster) ----
  // useTenantsRest() fetches the same-origin collection endpoint.
  ...tenantRestHandlers,
];

// Override-able error path. A test opts into the 500 branch with
// `server.use(tenantErrorHandler)` — server.use overrides this single handler,
// not the whole stack (resetHandlers in afterEach restores the happy path).
export const tenantErrorHandler = http.get(
  '/api/v1/tenants',
  () => HttpResponse.json({ error: 'boom' }, { status: 500 }),
);

// A loading variant: never resolves within the test window, so the role="status"
// skeleton stays painted long enough to assert against before any data lands.
export function tenantLoadingHandler(ms = 10_000) {
  return http.get('/api/v1/tenants', async () => {
    await delay(ms);
    return HttpResponse.json([]);
  });
}

// ---- LatestTenants (Apollo) error / loading override variants ----
// The happy LatestTenants query lives in the `handlers` array above; these two
// mirror the REST error/loading pair so a test can flip the GraphQL endpoint in
// isolation with `server.use(...)` (when exercising the real ApolloClient over
// MSW rather than MockedProvider's inline mocks).
export const latestTenantsErrorHandler = graphql.query('LatestTenants', () =>
  HttpResponse.json(
    { errors: [{ message: 'latest tenants query failed' }] },
    { status: 500 },
  ),
);

export function latestTenantsLoadingHandler(ms = 10_000) {
  return graphql.query('LatestTenants', async () => {
    await delay(ms);
    return HttpResponse.json({ data: { latestTenants: [] } });
  });
}
