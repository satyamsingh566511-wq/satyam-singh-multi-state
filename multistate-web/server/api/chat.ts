// server/api/chat.ts (Node + Hono)
import { Hono } from 'hono';
import { APICallError, streamText, type Message } from 'ai';
import { createOpenAICompatible } from '@ai-sdk/openai-compatible';
import { z } from 'zod';
import { tenantTools } from './chat-tools';

// THREAT MODEL: this proxy holds the upstream API key. The browser
// never sees it. The proxy receives only message history from the
// authenticated W4 D3 protected layout, NOT raw secrets, and only
// emits text/event-stream chunks back. See §9 for the buffering trap.
const upstream = createOpenAICompatible({
  name: 'spring-ai',
  baseURL: 'http://localhost:8080/ai',
});

const SYSTEM_PROMPT =
  'You are an assistant that helps engineers track multi-state tax compliance. ' +
  'When asked about a tenant, call lookupTenant first. When asked whether a ' +
  'tenant has nexus in a specific state, call nexusForState with the ' +
  'two-letter state code.';

// 5xx-mapping middleware: any error thrown while streaming (most importantly an
// upstream Spring AI 4xx/5xx surfaced by the provider as an APICallError) is
// converted into a clean sentinel message. toDataStreamResponse emits it as a
// `3:"..."` error frame, so the browser's useChat `error` field is set instead
// of the connection tearing mid-stream.
function toSentinelError(error: unknown): string {
  if (APICallError.isInstance(error)) {
    const status = error.statusCode ?? 'unknown';
    return `Upstream assistant error (HTTP ${status}). Please try again.`;
  }
  return error instanceof Error ? error.message : 'Unknown streaming error.';
}

// Runtime guard for the request body. c.req.json<T>() only narrows the
// TypeScript type — at runtime the client can send anything. Validate the
// minimum streamText needs (a non-empty array of role/content messages) so a
// malformed body returns a clear 400 instead of a cryptic internal throw deep
// inside streamText.
const chatRequestSchema = z.object({
  messages: z
    .array(
      z.object({
        role: z.enum(['system', 'user', 'assistant', 'data']),
        content: z.string(),
      }),
    )
    .min(1),
});

export const chat = new Hono().post('/chat', async (c) => {
  let body: unknown;
  try {
    body = await c.req.json();
  } catch {
    return c.json({ error: 'Request body must be valid JSON.' }, 400);
  }

  const parsed = chatRequestSchema.safeParse(body);
  if (!parsed.success) {
    return c.json(
      { error: 'Invalid chat request.', details: parsed.error.flatten() },
      400,
    );
  }
  const messages = parsed.data.messages as Message[];

  const result = streamText({
    model: upstream.chatModel('uptime-crew-assistant'),
    system: SYSTEM_PROMPT,
    messages,
    tools: tenantTools,
    // Let the assistant chain a tool call into a final reply in one request.
    maxSteps: 3,
    // Forward the browser's fetch abort so a cancelled request stops the
    // upstream LLM call too, rather than leaving it running server-side.
    abortSignal: c.req.raw.signal,
  });

  return result.toDataStreamResponse({
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    },
    getErrorMessage: toSentinelError,
  });
});
