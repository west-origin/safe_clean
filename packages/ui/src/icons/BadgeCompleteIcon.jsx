// Lucide: Check. Preserve the existing display size and inherited state colors.
import { Check as LucideIcon } from 'lucide-react';

export default function BadgeCompleteIcon({ size = 14, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
