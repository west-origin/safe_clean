import { Circle, Check } from 'lucide-react';

export default function VerifiedIcon({ size = 25, className }) {
  return <Circle size={size} strokeWidth={2} fill="currentColor" aria-hidden="true" focusable="false" className={className}>
    <Check width={24} height={24} strokeWidth={2} color="var(--color-surface-default)" />
  </Circle>;
}
