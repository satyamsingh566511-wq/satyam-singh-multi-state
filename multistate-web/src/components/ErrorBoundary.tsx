// src/components/ErrorBoundary.tsx
import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';

type Props = {
  readonly children: ReactNode;
  readonly fallback: (error: Error, reset: () => void) => ReactNode;
};

type State = { readonly error: Error | null };

export class ErrorBoundary extends Component<Props, State> {
  override state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    // Hook for Week 5 — the OTel browser SDK will forward these to the
    // backend. For now, surface to the console so the lab session has
    // something to grep.
    console.error('[ErrorBoundary]', error, info.componentStack);
  }

  private readonly reset = (): void => this.setState({ error: null });

  override render(): ReactNode {
    if (this.state.error) {
      return this.props.fallback(this.state.error, this.reset);
    }
    return this.props.children;
  }
}
