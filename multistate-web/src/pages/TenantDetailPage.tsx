// src/pages/TenantDetailPage.tsx
import { useState } from 'react';
import type { ReactElement } from 'react';
import { useTenant } from '../hooks/useTenant';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';

export function TenantDetailPage(): ReactElement {
  // (1) `threshold` is owned HERE — the page is the source of truth.
  //     ThresholdSlider mutates it via the onChange prop; ThresholdReadout
  //     reads it via the value prop. Two siblings, one source.
  const [threshold, setThreshold] = useState<number>(50);

  const { data, loading, error } = useTenant('stub-id-1');

  if (loading) return <p>Loading…</p>;
  if (error) return <p role="alert">Failed to load: {error}</p>;
  if (data === null) return <p>Not found.</p>;

  return (
    <main>
      <h1>Tenant {data.id}</h1>
      <dl>
        <dt>primaryState</dt>
        <dd>{data.primaryState}</dd>
        <dt>stateCount</dt>
        <dd>{data.stateCount}</dd>
        <dt>totalAllocation</dt>
        <dd>{data.totalAllocation}</dd>
      </dl>

      {/* (2) The slider is the controlled input — React owns its value. */}
      <ThresholdSlider value={threshold} onChange={setThreshold} />

      {/* (3) The readout is a sibling that sees the SAME source of truth. */}
      <ThresholdReadout value={threshold} />
    </main>
  );
}
