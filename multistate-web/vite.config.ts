import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // The browser's /api/chat request lands at the Hono server (server/index.ts).
      // changeOrigin keeps the upstream happy; ws:false because this is plain SSE.
      '/api/chat': {
        target: 'http://localhost:3001',
        changeOrigin: true,
      },
      // GraphQL goes to the Spring backend. Proxying it through Vite keeps the
      // browser request same-origin, so the backend needs no CORS config (it has
      // none) — same pattern as /api/chat above.
      '/graphql': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // The TenantSummaryPage roster (useTenantsRest) hits the Spring REST
      // surface same-origin; proxy it like /graphql so the backend needs no CORS.
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
