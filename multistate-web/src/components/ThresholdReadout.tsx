// src/components/ThresholdReadout.tsx
import type { ReactElement } from 'react';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

// Pure read-only sibling. It shares no wiring with ThresholdSlider — both
// subscribe independently to the same `threshold` slice, which is what proves
// the store pattern (no prop threading between siblings).
// `role="status"` makes it a polite live region — assistive tech announces the
// new threshold as the slider moves, and tests can target it by role.
export function ThresholdReadout(): ReactElement {
  const value = useTenantFilterStore((s) => s.threshold);
  return (
    <p role="status" className="badge">
      Threshold: {value}%
    </p>
  );
}
