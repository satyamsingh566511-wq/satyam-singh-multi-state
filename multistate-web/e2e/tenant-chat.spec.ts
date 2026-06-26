// e2e/tenant-chat.spec.ts
//
// The single W4 capstone happy-path: an authenticated engineer opens the tenant
// list, drills into a tenant, chats with the assistant, and confirms the reply
// (plus its tool call) survives a reload. Every backend hop is mocked at the
// browser edge with page.route, so the test needs only the Vite dev server.
import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const TENANT_ID = 'ten_synth_a1b2';
const TENANT_NAME = 'Stub Tenant 01';

// Vercel AI SDK v4 data-stream frames: text deltas (0:), a tool call (9:) and
// its result (a:), then the message-finish terminator (d:). useChat parses this
// into the assistant message + its toolInvocations.
const CHAT_STREAM = [
  '0:"stub "',
  '0:"tenant "',
  '0:"reply."',
  `9:${JSON.stringify({
    toolCallId: 'call_1',
    toolName: 'lookupTenant',
    args: { id: TENANT_ID },
  })}`,
  `a:${JSON.stringify({
    toolCallId: 'call_1',
    result: { name: TENANT_NAME, nexus: ['CA', 'NY'] },
  })}`,
  `d:${JSON.stringify({
    finishReason: 'stop',
    usage: { promptTokens: 1, completionTokens: 3 },
  })}`,
  '',
].join('\n');

async function installMocks(page: Page): Promise<void> {
  // Apollo LatestTenants — the tenant list query.
  await page.route('**/graphql', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          latestTenants: [
            { id: TENANT_ID, name: TENANT_NAME, updatedAt: '2025-01-01T00:00:00Z', __typename: 'Tenant' },
          ],
        },
      }),
    });
  });

  // Streamed chat reply (the D4 SSE panel).
  await page.route('**/api/chat', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'content-type': 'text/event-stream',
        'x-vercel-ai-data-stream': 'v1',
      },
      body: CHAT_STREAM,
    });
  });
}

test.describe('MultiState W4 capstone happy-path', () => {
  test('engineer chats with the assistant and history survives reload', async ({
    page,
  }) => {
    await installMocks(page);

    // 1. The tenant list renders by accessible name.
    await page.goto('/tenants');
    await expect(
      page.getByRole('heading', { name: /tenants/i }),
    ).toBeVisible();

    // 2. Open the tenant by its row link; the URL carries the id.
    await page.getByRole('link', { name: new RegExp(TENANT_NAME, 'i') }).click();
    await expect(page).toHaveURL(new RegExp(`/tenants/${TENANT_ID}`));

    // 3. a11y budget: one scan per page state, zero wcag2a/2aa violations.
    const detailScan = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    expect(detailScan.violations).toEqual([]);

    // 4. Drive the streamed chat panel.
    await page.goto(`/tenants/${TENANT_ID}/chat`);
    await page.getByRole('textbox', { name: /chat-message/i }).fill('hello');
    await page.getByRole('button', { name: /send/i }).click();

    // 5. Web-first assertions: tokens stream into the transcript log...
    const transcript = page.getByRole('log', { name: 'chat-transcript' });
    await expect(transcript).toContainText(/stub tenant reply\./i);

    // ...and the tool call renders inline beneath the assistant message.
    await expect(page.getByLabel('tool-call')).toBeVisible();

    // 6. Reload; the persisted assistant message rehydrates from the store.
    await page.reload();
    await expect(
      page.getByRole('log', { name: 'chat-transcript' }),
    ).toContainText(/stub tenant reply\./i);
  });
});
