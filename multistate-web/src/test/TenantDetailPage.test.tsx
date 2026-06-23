// src/test/TenantDetailPage.test.tsx
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TenantDetailPage } from '../pages/TenantDetailPage';
import type { Tenant } from '../types/tenant';

const MOCK: Tenant = {
  id: 'stub-id-1',
  primaryState: 'CA',
  stateCount: 5,
  totalAllocation: '12500.00',
  lines: [
    { id: 'line-1', amount: '100.00' },
    { id: 'line-2', amount: '250.00' },
  ],
};

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.resolve(new Response(JSON.stringify(MOCK)))),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('TenantDetailPage', () => {
  it('renders_entityIdAndSampleField_fromMockJson', async () => {
    render(<TenantDetailPage />);

    await waitFor(() =>
      expect(screen.getByRole('heading')).toHaveTextContent('stub-id-1'),
    );
    expect(screen.getByText('CA')).toBeInTheDocument();
  });

  it('movingSlider_updatesReadout_provingLiftedState', async () => {
    const user = userEvent.setup();
    render(<TenantDetailPage />);
    await waitFor(() => screen.getByRole('heading'));

    // Default lifted value is 50; one ArrowRight step lands on 51.
    const slider = screen.getByLabelText(/Threshold/i);
    slider.focus();
    await user.keyboard('{ArrowRight}');

    expect(screen.getByRole('status')).toHaveTextContent(/Threshold:\s*51%/);
  });

  it('rendersError_whenFetchFails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new Error('boom'))),
    );

    render(<TenantDetailPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Failed to load: boom',
    );
  });
});
