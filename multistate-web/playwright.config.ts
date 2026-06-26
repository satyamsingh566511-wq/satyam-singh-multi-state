// playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

// The happy-path E2E mocks every network call at the browser edge (page.route),
// so it needs only the Vite dev server up — no Spring backend, no LLM upstream.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    storageState: 'e2e/.auth/user.json',
  },
  globalSetup: './e2e/global-setup.ts',
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
