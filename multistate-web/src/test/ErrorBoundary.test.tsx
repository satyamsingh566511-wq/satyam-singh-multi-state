// src/test/ErrorBoundary.test.tsx
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { ErrorBoundary } from '../components/ErrorBoundary';

function Boom({ explode }: { readonly explode: boolean }): ReactElement {
  if (explode) throw new Error('kaboom');
  return <p>all good</p>;
}

function fallback(error: Error, reset: () => void): ReactElement {
  return (
    <div role="alert">
      <p>{error.message}</p>
      <button type="button" onClick={reset}>
        Try again
      </button>
    </div>
  );
}

describe('ErrorBoundary', () => {
  afterEach(() => vi.restoreAllMocks());

  it('renders its children when nothing throws', () => {
    render(
      <ErrorBoundary fallback={fallback}>
        <Boom explode={false} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('all good')).toBeInTheDocument();
  });

  it('renders the fallback with the error message when a child throws', () => {
    // componentDidCatch logs to console.error; silence it for a clean run.
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ErrorBoundary fallback={fallback}>
        <Boom explode={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('kaboom');
  });

  it('recovers to the children after reset once the child stops throwing', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const user = userEvent.setup();

    function Flaky(): ReactElement {
      return <Boom explode={false} />;
    }

    const { rerender } = render(
      <ErrorBoundary fallback={fallback}>
        <Boom explode={true} />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();

    // Swap in a non-throwing child, then click reset: the boundary remounts the
    // subtree and lands on the healthy render.
    rerender(
      <ErrorBoundary fallback={fallback}>
        <Flaky />
      </ErrorBoundary>,
    );
    await user.click(screen.getByRole('button', { name: /try again/i }));

    expect(screen.getByText('all good')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
