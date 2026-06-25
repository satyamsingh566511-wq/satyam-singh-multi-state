// src/test/sse-handlers.ts
import { http, HttpResponse } from 'msw';

// Encode Vercel-AI-SDK (v4) data-stream protocol frames. Each frame is a
// single `TYPE_ID:JSON\n` line:
//   0:"text chunk"      -- a text delta
//   d:{finishReason...} -- the finish event
// A single TextEncoder is reused for every frame.
const encoder = new TextEncoder();

function encodeFrame(prefix: string, payload: unknown): Uint8Array {
  const line = `${prefix}:${JSON.stringify(payload)}\n`;
  return encoder.encode(line);
}

// The three text deltas the stub assistant "streams". Exported so tests can
// assert against the concatenation ("stub tenant reply.") and reuse them.
export const STUB_TEXT_FRAMES = ['stub ', 'tenant ', 'reply.'] as const;
export const STUB_REPLY = STUB_TEXT_FRAMES.join('');

function dataStreamResponse(stream: ReadableStream<Uint8Array>) {
  return new HttpResponse(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      'X-Vercel-AI-Data-Stream': 'v1',
    },
  });
}

// The default handler: enqueue all three text frames and the finish terminator,
// then close immediately. The token-streaming + persistence tests use this.
export const sseHandlers = [
  http.post('/api/chat', () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        for (const text of STUB_TEXT_FRAMES) {
          controller.enqueue(encodeFrame('0', text));
        }
        controller.enqueue(
          encodeFrame('d', {
            finishReason: 'stop',
            usage: { promptTokens: 1, completionTokens: 3 },
          }),
        );
        controller.close();
      },
    });
    return dataStreamResponse(stream);
  }),
];

// A slow variant for the Stop-mid-stream test: the first token streams
// immediately (so the transcript is observably mid-reply), then the remaining
// frames are spaced out by `gapMs`, leaving a wide window to click Stop before
// the reply completes. Enqueues are guarded so that once the consumer cancels
// (the abort bridge in setup.ts cancels the reader), no write throws. Exposed
// as a factory so a test can install it via server.use(...).
export function slowSseHandler(gapMs = 500) {
  return http.post('/api/chat', () => {
    let cancelled = false;
    const stream = new ReadableStream<Uint8Array>({
      async start(controller) {
        const safe = (frame: Uint8Array): void => {
          if (cancelled) return;
          try {
            controller.enqueue(frame);
          } catch {
            cancelled = true; // consumer is gone
          }
        };

        // First token right away; the rest after a gap.
        safe(encodeFrame('0', STUB_TEXT_FRAMES[0]));
        for (const text of STUB_TEXT_FRAMES.slice(1)) {
          await new Promise((resolve) => setTimeout(resolve, gapMs));
          safe(encodeFrame('0', text));
        }
        await new Promise((resolve) => setTimeout(resolve, gapMs));
        safe(
          encodeFrame('d', {
            finishReason: 'stop',
            usage: { promptTokens: 1, completionTokens: 3 },
          }),
        );
        if (!cancelled) {
          try {
            controller.close();
          } catch {
            /* already closed by consumer cancel */
          }
        }
      },
      cancel() {
        cancelled = true;
      },
    });
    return dataStreamResponse(stream);
  });
}
