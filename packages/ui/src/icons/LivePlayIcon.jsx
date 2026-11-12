// Lucide: Play. Preserve the existing display size and inherited state colors.
import { Play as LucideIcon } from 'lucide-react';

export default function LivePlayIcon({ size = 35, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
