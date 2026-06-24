// src/components/ErrorBoundary.tsx
import { Component, Fragment } from 'react';
import type { ErrorInfo, ReactNode } from 'react';

type Props = {
  readonly children: ReactNode;
  readonly fallback: (error: Error, reset: () => void) => ReactNode;
};

type State = { readonly error: Error | null; readonly resetKey: number };

export class ErrorBoundary extends Component<Props, State> {
  override state: State = { error: null, resetKey: 0 };

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    // Hook for Week 5 — the OTel browser SDK will forward these to the
    // backend. For now, surface to the console so the lab session has
    // something to grep.
    console.error('[ErrorBoundary]', error, info.componentStack);
  }

  // Clearing the error AND bumping the key. `key` is how React decides whether
  // to keep or replace a component instance: a new key forces it to discard the
  // old child subtree and mount a fresh one, so reset always lands on initial
  // child state rather than reviving a stale instance.
  private readonly reset = (): void =>
    this.setState((s) => ({ error: null, resetKey: s.resetKey + 1 }));

  override render(): ReactNode {
    if (this.state.error) {
      return this.props.fallback(this.state.error, this.reset);
    }
    return <Fragment key={this.state.resetKey}>{this.props.children}</Fragment>;
  }
}
