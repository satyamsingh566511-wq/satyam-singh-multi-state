// server/api/chat-tools.ts
import { tool } from 'ai';
import { z } from 'zod';

// The two tools the assistant may call. Each declares a zod-typed parameter
// schema (so the model is forced to emit well-shaped arguments) and an async
// `execute` that hits the W3 D2 REST backend. The values returned here are
// streamed back to the model, which then quotes them in its final reply.
export const tenantTools = {
  lookupTenant: tool({
    description:
      'Look up a single tenant by id. ' +
      'Returns the canonical record stored in the W3 D2 REST backend.',
    parameters: z.object({
      id: z.string(),
    }),
    execute: async ({ id }) => {
      const res = await fetch(`http://localhost:8080/api/v1/tenants/${id}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    },
  }),
  nexusForState: tool({
    description:
      'Search the tenant corpus by state. ' +
      'Returns a small array the assistant can quote inline.',
    parameters: z.object({
      state: z.string(),
    }),
    execute: async ({ state }) => {
      const res = await fetch(
        `http://localhost:8080/api/v1/tenants?state=${state}`,
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    },
  }),
};
