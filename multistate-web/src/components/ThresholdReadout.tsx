// src/components/ThresholdReadout.tsx
import type { ReactElement } from 'react';

type Props = { readonly value: number };

// Pure read-only sibling. It shares no wiring with ThresholdSlider — it just
// receives the same lifted `value` prop, which is what proves the pattern.
// `role="status"` makes it a polite live region — assistive tech announces the
// new threshold as the slider moves, and tests can target it by role.
export function ThresholdReadout({ value }: Props): ReactElement {
  return <p role="status">Threshold: {value}%</p>;
}
