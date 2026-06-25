// src/test/TenantChatPanel.error.test.tsx
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TenantChatPanel } from '../pages/TenantChatPanel';
import { useTenantChatStore } from '../stores/useTenantChatStore';
import { server } from './server';
import './server'; // installs MSW lifecycle + sseHandlers

function renderPanel(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/tenants/${id}/chat`]}>
      <Routes>
        <Route path="/tenants/:id/chat" element={<TenantChatPanel />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('TenantChatPanel (upstream error)', () => {
  beforeEach(() => {
    useTenantChatStore.setState({ messages: [] });
  });

  it('shows an alert pane when the chat endpoint returns 500', async () => {
    server.use(
      http.post('/api/chat', () =>
        HttpResponse.json({ error: 'upstream exploded' }, { status: 500 }),
      ),
    );

    renderPanel('err-500');
    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toBeInTheDocument(),
    );
  });

  it('does not surface an assistant reply on a 500', async () => {
    server.use(
      http.post('/api/chat', () =>
        HttpResponse.json({ error: 'upstream exploded' }, { status: 500 }),
      ),
    );

    const { container } = renderPanel('err-noreply');
    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(
      container.querySelector('li[data-role="assistant"]'),
    ).not.toBeInTheDocument();
  });

  it('clears the replying status once the error lands', async () => {
    server.use(
      http.post('/api/chat', () =>
        HttpResponse.json({ error: 'nope' }, { status: 503 }),
      ),
    );

    renderPanel('err-503');
    await userEvent.type(screen.getByRole('textbox'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
