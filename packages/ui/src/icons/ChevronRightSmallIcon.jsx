// Lucide: ChevronRight. Preserve the existing display size and inherited state colors.
import { ChevronRight as LucideIcon } from 'lucide-react';

export default function ChevronRightSmallIcon({ size = 18, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
