// Lucide: Pause. Preserve the existing display size and inherited state colors.
import { Pause as LucideIcon } from 'lucide-react';

export default function LivePauseIcon({ size = 35, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
