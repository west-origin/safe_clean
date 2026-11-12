// Lucide: Clock. Preserve the existing display size and inherited state colors.
import { Clock as LucideIcon } from 'lucide-react';

export default function InfoClockIcon({ size = 20, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
