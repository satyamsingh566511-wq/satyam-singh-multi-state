// src/pages/TenantChatPanel.tsx
import type { ReactElement } from 'react';
import { useEffect, useRef } from 'react';
import { useChat } from '@ai-sdk/react';
import { useParams } from 'react-router-dom';
import { useTenantChatStore } from '../stores/useTenantChatStore';
import { ToolCallCard } from './ToolCallCard';

export function TenantChatPanel(): ReactElement {
  const { id = '' } = useParams<{ id: string }>();
  const persist = useTenantChatStore((s) => s.appendAssistantMessage);

  const {
    messages,
    input,
    handleInputChange,
    handleSubmit,
    isLoading,
    stop,
    reload,
    error,
  } = useChat({
    api: '/api/chat',
    // Stable, tenant-scoped id so each tenant keeps its own chat instance.
    id: `tenant-${id}`,
    onFinish: (message) => {
      // CRITICAL: only persist on completion. Writing partial tokens to
      // Zustand mid-stream breaks the persist middleware's rehydration, so a
      // Stopped partial reply must never be written here — see §9.
      persist(message);
    },
  });

  // Auto-scroll the transcript to the newest message on every change.
  const endRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <main className="page">
      <ul aria-label="chat-transcript">
        {messages.map((m) => (
          <li key={m.id} data-role={m.role}>
            <span>
              {m.role}: {m.content}
            </span>
            {(m.toolInvocations ?? []).map((invocation) => (
              <ToolCallCard
                key={invocation.toolCallId}
                invocation={invocation}
              />
            ))}
          </li>
        ))}
      </ul>
      <div ref={endRef} />

      {isLoading && <p role="status">Assistant is replying...</p>}
      {error && <p role="alert">Error: {error.message}</p>}

      <form aria-label="chat-input" onSubmit={handleSubmit}>
        <input
          aria-label="chat-message"
          value={input}
          onChange={handleInputChange}
          placeholder="Ask about a tenant…"
        />
        <button type="submit" disabled={input.trim() === ''}>
          Send
        </button>
        <button type="button" onClick={stop} disabled={!isLoading}>
          Stop
        </button>
        <button
          type="button"
          onClick={() => {
            void reload();
          }}
          disabled={isLoading}
        >
          Regenerate
        </button>
      </form>
    </main>
  );
}
