// src/test/setupTests.ts
//
// The single Vitest setup module (see vitest.config.ts → setupFiles). It pulls
// in the W4 D4 polyfills + MSW lifecycle from ./setup, then layers on the
// jest-axe matcher so any test can call `expect(container).toHaveNoViolations()`.
import './setup'; // jest-dom, scrollIntoView/fetch/localStorage polyfills, MSW lifecycle, cleanup
import { expect } from 'vitest';
import { toHaveNoViolations } from 'jest-axe';

// jest-axe matcher available in every test file.
expect.extend(toHaveNoViolations);

// Teach TypeScript (and Vitest's expect) that the matcher exists. The type
// parameter list MUST mirror Vitest's own `Assertion<T = any>` declaration
// (TS2428 otherwise), so the `any` here is structural, not a loose type — hence
// the targeted disable.
declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  interface Assertion<T = any> {
    toHaveNoViolations(): T;
  }
  interface AsymmetricMatchersContaining {
    toHaveNoViolations(): void;
  }
}
