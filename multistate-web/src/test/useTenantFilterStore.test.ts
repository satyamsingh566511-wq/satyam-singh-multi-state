// src/test/useTenantFilterStore.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

beforeEach(() => {
  // Zustand v5: reset the store to its initial state before each test so
  // tests do not bleed into each other.
  useTenantFilterStore.setState(useTenantFilterStore.getInitialState(), true);
});

describe('useTenantFilterStore', () => {
  it('setSearchText updates state.searchText', () => {
    useTenantFilterStore.getState().setSearchText('foo');
    expect(useTenantFilterStore.getState().searchText).toBe('foo');
  });

  it('setThreshold updates state.threshold', () => {
    useTenantFilterStore.getState().setThreshold(80);
    expect(useTenantFilterStore.getState().threshold).toBe(80);
  });

  it('setStateFilter is last-write-wins', () => {
    useTenantFilterStore.getState().setStateFilter(['A', 'B']);
    useTenantFilterStore.getState().setStateFilter(['C']);
    expect(useTenantFilterStore.getState().stateFilter).toEqual(['C']);
  });

  it('reset() returns state to the initial shape', () => {
    useTenantFilterStore.getState().setSearchText('foo');
    useTenantFilterStore.getState().setThreshold(80);
    useTenantFilterStore.getState().reset();
    expect(useTenantFilterStore.getState().searchText).toBe('');
    expect(useTenantFilterStore.getState().threshold).toBe(50);
  });
});
