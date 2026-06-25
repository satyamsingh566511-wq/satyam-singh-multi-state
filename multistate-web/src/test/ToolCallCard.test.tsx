// src/test/ToolCallCard.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ToolInvocation } from 'ai';
import { ToolCallCard } from '../pages/ToolCallCard';

const partialCall: ToolInvocation = {
  state: 'partial-call',
  toolCallId: 'call-1',
  toolName: 'lookupTenant',
  args: { id: 'tnt' },
};

const call: ToolInvocation = {
  state: 'call',
  toolCallId: 'call-2',
  toolName: 'lookupTenant',
  args: { id: 'tnt-42' },
};

const result: ToolInvocation = {
  state: 'result',
  toolCallId: 'call-3',
  toolName: 'nexusForState',
  args: { state: 'CA' },
  result: { id: 'tnt-42', name: 'Acme', state: 'CA' },
};

describe('ToolCallCard', () => {
  it('renders the tool name for a partial-call invocation', () => {
    render(<ToolCallCard invocation={partialCall} />);
    expect(screen.getByText(/called lookupTenant/)).toBeInTheDocument();
  });

  it('does not render a result pane while in partial-call state', () => {
    render(<ToolCallCard invocation={partialCall} />);
    expect(screen.queryByTestId('tool-result')).not.toBeInTheDocument();
  });

  it('exposes the invocation state via data-state', () => {
    render(<ToolCallCard invocation={partialCall} />);
    expect(screen.getByLabelText('tool-call')).toHaveAttribute(
      'data-state',
      'partial-call',
    );
  });

  it('renders the call arguments for a call invocation', () => {
    render(<ToolCallCard invocation={call} />);
    expect(screen.getByText(/called lookupTenant/)).toBeInTheDocument();
    expect(screen.getByText(/"id": "tnt-42"/)).toBeInTheDocument();
  });

  it('does not render a result pane while in call state', () => {
    render(<ToolCallCard invocation={call} />);
    expect(screen.queryByTestId('tool-result')).not.toBeInTheDocument();
  });

  it('renders the result payload once in result state', () => {
    render(<ToolCallCard invocation={result} />);
    const pane = screen.getByTestId('tool-result');
    expect(pane).toBeInTheDocument();
    expect(pane).toHaveTextContent('"name": "Acme"');
    expect(pane).toHaveTextContent('"state": "CA"');
  });

  it('still shows the tool name and args in result state', () => {
    render(<ToolCallCard invocation={result} />);
    expect(screen.getByText(/called nexusForState/)).toBeInTheDocument();
    // "state": "CA" appears in both the args pane and the result pane.
    expect(screen.getAllByText(/"state": "CA"/).length).toBeGreaterThanOrEqual(
      1,
    );
  });
});
