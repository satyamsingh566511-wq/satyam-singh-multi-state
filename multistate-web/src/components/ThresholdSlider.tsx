// src/components/ThresholdSlider.tsx
import type { KeyboardEvent, ReactElement } from 'react';
import { useTenantFilterStore } from '../stores/useTenantFilterStore';

const MIN = 0;
const MAX = 100;
const STEP = 1;

const clamp = (n: number): number => Math.min(MAX, Math.max(MIN, n));

// Controlled input wired to the Zustand store instead of a lifted prop pair.
// The component subscribes to ONLY the `threshold` slice and its setter, so a
// keystroke here re-renders this control and the readout — nothing else.
//
// Arrow-key stepping is handled explicitly (with preventDefault so a real
// browser's native range stepping doesn't double-fire). jsdom doesn't move a
// range on arrow keys, so this is also what makes the control testable.
export function ThresholdSlider(): ReactElement {
  const value = useTenantFilterStore((s) => s.threshold);
  const setThreshold = useTenantFilterStore((s) => s.setThreshold);

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>): void => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
      e.preventDefault();
      setThreshold(clamp(value + STEP));
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
      e.preventDefault();
      setThreshold(clamp(value - STEP));
    }
  };

  return (
    <input
      className="threshold__slider"
      type="range"
      min={MIN}
      max={MAX}
      step={STEP}
      value={value}
      aria-label="Threshold"
      onChange={(e) => setThreshold(Number(e.currentTarget.value))}
      onKeyDown={onKeyDown}
    />
  );
}
