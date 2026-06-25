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
    },
  },
})
