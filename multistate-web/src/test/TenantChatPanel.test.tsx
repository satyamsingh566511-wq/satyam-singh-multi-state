// src/test/TenantChatPanel.test.tsx
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TenantChatPanel } from '../pages/TenantChatPanel';
import { useTenantChatStore } from '../stores/useTenantChatStore';
import { server } from './server';
import { slowSseHandler, STUB_REPLY } from './sse-handlers';
import './server'; // installs MSW lifecycle + sseHandlers

function renderPanel(id = 'tnt-1') {
  return render(
    <MemoryRouter initialEntries={[`/tenants/${id}/chat`]}>
      <Routes>
        <Route path="/tenants/:id/chat" element={<TenantChatPanel />} />
      </Routes>
    </MemoryRouter>,
  );
}

function transcriptText(): string {
  return screen.getByRole('log', { name: 'chat-transcript' }).textContent ?? '';
}

describe('TenantChatPanel', () => {
  beforeEach(() => {
    useTenantChatStore.setState({ messages: [] });
  });

  it('streams the stub assistant reply token-by-token', async () => {
    renderPanel('stream');

    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() =>
      expect(screen.getByText(/stub tenant reply\./i)).toBeInTheDocument(),
    );
  });

  it('renders the finished assistant message with data-role="assistant"', async () => {
    const { container } = renderPanel('role');

    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      const assistant = container.querySelector('[data-role="assistant"]');
      expect(assistant?.textContent).toMatch(/stub tenant reply\./i);
    });
  });

  it('disables Send when the input is empty and enables it once typed', async () => {
    renderPanel('disable');
    const send = screen.getByRole('button', { name: 'Send' });
    expect(send).toBeDisabled();

    await userEvent.type(screen.getByRole('textbox'), 'hi');
    expect(send).toBeEnabled();
  });

  it('shows the "Assistant is replying..." status while streaming', async () => {
    server.use(slowSseHandler());
    renderPanel('status');

    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      /assistant is replying/i,
    );
  });

  it('stops a mid-stream reply, leaving a partial transcript', async () => {
    server.use(slowSseHandler());
    renderPanel('stop');

    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    // Wait until the first token has streamed in but the reply is incomplete.
    await screen.findByText(/stub/i);
    expect(transcriptText()).not.toContain(STUB_REPLY);

    await userEvent.click(screen.getByRole('button', { name: 'Stop' }));

    // isLoading flips false (status pane disappears)...
    await waitFor(() =>
      expect(screen.queryByRole('status')).not.toBeInTheDocument(),
    );
    // ...and the transcript never reaches the full stub reply.
    expect(transcriptText()).not.toContain(STUB_REPLY);
  });

  it('regenerate fires a second POST to /api/chat', async () => {
    let posts = 0;
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/api/chat')) {
        posts += 1;
      }
    });

    renderPanel('regen');
    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    await waitFor(() =>
      expect(screen.getByText(/stub tenant reply\./i)).toBeInTheDocument(),
    );
    expect(posts).toBe(1);

    await userEvent.click(screen.getByRole('button', { name: 'Regenerate' }));
    await waitFor(() => expect(posts).toBe(2));
  });
});
