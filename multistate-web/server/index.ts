// server/index.ts
import { serve } from '@hono/node-server';
import { Hono } from 'hono';
import { chat } from './api/chat';

// The Hono proxy that fronts the upstream Spring AI endpoint. The browser hits
// `/api/chat` (proxied here by Vite in dev — see vite.config.ts) and this
// process holds the upstream credentials so they never reach the client.
const app = new Hono();

app.route('/api', chat);

const port = 3001;

serve({ fetch: app.fetch, port }, (info) => {
  console.log(`Hono chat proxy listening on http://localhost:${info.port}`);
});
