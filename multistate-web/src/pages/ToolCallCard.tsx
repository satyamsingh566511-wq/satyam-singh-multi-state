// src/pages/ToolCallCard.tsx
import type { ReactElement } from 'react';
import type { ToolInvocation } from 'ai';

interface ToolCallCardProps {
  readonly invocation: ToolInvocation;
}

// Presentational only. Renders the tool name and the call arguments for every
// invocation, and additionally the result payload once the invocation has
// reached the 'result' state.
export function ToolCallCard({ invocation }: ToolCallCardProps): ReactElement {
  return (
    <aside aria-label="tool-call" data-state={invocation.state}>
      <p>called {invocation.toolName}</p>
      <pre>{JSON.stringify(invocation.args, null, 2)}</pre>
      {invocation.state === 'result' && (
        <pre data-testid="tool-result">
          {JSON.stringify(invocation.result, null, 2)}
        </pre>
      )}
    </aside>
  );
}
