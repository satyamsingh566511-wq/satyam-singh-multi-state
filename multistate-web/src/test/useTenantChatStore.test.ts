// src/test/useTenantChatStore.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import type { Message } from 'ai';
import { useTenantChatStore } from '../stores/useTenantChatStore';

function assistant(id: string, content: string): Message {
  return { id, role: 'assistant', content };
}

describe('useTenantChatStore', () => {
  beforeEach(() => {
    useTenantChatStore.setState({ messages: [] });
    localStorage.clear();
  });

  it('starts with an empty transcript', () => {
    expect(useTenantChatStore.getState().messages).toEqual([]);
  });

  it('appendAssistantMessage adds a message', () => {
    useTenantChatStore.getState().appendAssistantMessage(assistant('m1', 'hi'));
    expect(useTenantChatStore.getState().messages).toHaveLength(1);
    expect(useTenantChatStore.getState().messages[0]?.content).toBe('hi');
  });

  it('appends messages in order', () => {
    const { appendAssistantMessage } = useTenantChatStore.getState();
    appendAssistantMessage(assistant('m1', 'first'));
    appendAssistantMessage(assistant('m2', 'second'));
    expect(
      useTenantChatStore.getState().messages.map((m) => m.content),
    ).toEqual(['first', 'second']);
  });

  it('clear() empties the transcript', () => {
    useTenantChatStore.getState().appendAssistantMessage(assistant('m1', 'hi'));
    useTenantChatStore.getState().clear();
    expect(useTenantChatStore.getState().messages).toEqual([]);
  });

  it('writes the transcript through to localStorage', () => {
    useTenantChatStore
      .getState()
      .appendAssistantMessage(assistant('m1', 'persisted'));
    const raw = localStorage.getItem('uc:tenant-chat');
    expect(raw).toContain('persisted');
  });

  it('rehydrates the persisted transcript after a fresh load', async () => {
    // 1. Append (the persist middleware writes it through to localStorage).
    useTenantChatStore
      .getState()
      .appendAssistantMessage(assistant('m1', 'survives reload'));
    // Capture the exact serialised payload the middleware wrote.
    const persisted = localStorage.getItem('uc:tenant-chat');
    expect(persisted).not.toBeNull();

    // 2. Simulate a fresh page: blow away in-memory state. setState itself
    //    re-persists (the middleware wraps set), so restore the captured
    //    payload afterwards to stand in for what a real reload would find.
    useTenantChatStore.setState({ messages: [] });
    localStorage.setItem('uc:tenant-chat', persisted as string);
    expect(useTenantChatStore.getState().messages).toEqual([]);

    // 3. Rehydrate from storage, exactly as the middleware does on mount.
    await useTenantChatStore.persist.rehydrate();

    const messages = useTenantChatStore.getState().messages;
    expect(messages).toHaveLength(1);
    expect(messages[0]?.content).toBe('survives reload');
  });
});
