// Lucide: ChevronRight. Preserve the existing display size and inherited state colors.
import { ChevronRight as LucideIcon } from 'lucide-react';

export default function ChevronRightTinyIcon({ size = 12, className }) {
  return <LucideIcon width={(size * 7) / 12} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
