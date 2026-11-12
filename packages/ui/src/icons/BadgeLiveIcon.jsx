// Lucide: Dot. Preserve the existing display size and inherited state colors.
import { Dot as LucideIcon } from 'lucide-react';

export default function BadgeLiveIcon({ size = 14, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
