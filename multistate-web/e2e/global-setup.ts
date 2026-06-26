// e2e/global-setup.ts
//
// Runs once before the suite: drive the real login form, then persist the
// authenticated browser state (the stub JWT in localStorage) to disk. Every
// spec then starts already signed in via use.storageState — no per-test login.
import { chromium, type FullConfig } from '@playwright/test';

export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use.baseURL ?? 'http://localhost:5173';
  const browser = await chromium.launch();
  const page = await browser.newPage();

  await page.goto(`${baseURL}/login`);
  await page.getByLabel(/email/i).fill('engineer@uptimecrew.example.internal');
  await page.getByLabel(/password/i).fill('synthetic-test-pwd');
  await page.getByRole('button', { name: /sign in/i }).click();

  // The protected layout redirects to the tenant list once the JWT is set.
  await page.waitForURL(/\/tenants/);

  await page.context().storageState({ path: 'e2e/.auth/user.json' });
  await browser.close();
}
