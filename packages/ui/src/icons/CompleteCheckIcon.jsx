import { Circle, Check } from 'lucide-react';

export default function CompleteCheckIcon({ size = 56, className }) {
  return <Circle size={size} strokeWidth={2} fill="currentColor" aria-hidden="true" focusable="false" className={className}>
    <Check width={24} height={24} strokeWidth={2} color="var(--color-text-inverse)" />
  </Circle>;
}
