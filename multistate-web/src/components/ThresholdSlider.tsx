// src/components/ThresholdSlider.tsx
import type { KeyboardEvent, ReactElement } from 'react';

type Props = {
  readonly value: number; // 0..100
  readonly onChange: (next: number) => void;
};

const MIN = 0;
const MAX = 100;
const STEP = 1;

const clamp = (n: number): number => Math.min(MAX, Math.max(MIN, n));

// Controlled input: React owns `value`. The component holds NO state of its
// own — every drag/keystroke round-trips through the parent via `onChange`.
//
// Arrow-key stepping is handled explicitly (with preventDefault so a real
// browser's native range stepping doesn't double-fire). jsdom doesn't move a
// range on arrow keys, so this is also what makes the control testable.
export function ThresholdSlider({ value, onChange }: Props): ReactElement {
  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>): void => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
      e.preventDefault();
      onChange(clamp(value + STEP));
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
      e.preventDefault();
      onChange(clamp(value - STEP));
    }
  };

  return (
    <label>
      Threshold{' '}
      <input
        type="range"
        min={MIN}
        max={MAX}
        step={STEP}
        value={value}
        aria-label="Threshold"
        onChange={(e) => onChange(Number(e.currentTarget.value))}
        onKeyDown={onKeyDown}
      />
    </label>
  );
}
