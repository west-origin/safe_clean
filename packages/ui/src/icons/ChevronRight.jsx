// Lucide: ChevronRight. Preserve the existing display size and inherited state colors.
import { ChevronRight as LucideIcon } from 'lucide-react';

export default function ChevronRight({ size = 16.5, className }) {
  return <LucideIcon width={size * (9.5 / 16.5)} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
