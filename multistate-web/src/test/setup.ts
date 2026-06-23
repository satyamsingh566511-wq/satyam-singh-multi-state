// src/test/setup.ts
import '@testing-library/jest-dom';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom is reused across tests in a file — unmount between cases so queries
// never see a stale component tree.
afterEach(() => {
  cleanup();
});
