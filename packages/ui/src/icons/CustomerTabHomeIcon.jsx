// Lucide: House. Preserve the existing display size and inherited state colors.
import { House as LucideIcon } from 'lucide-react';

export default function CustomerTabHomeIcon({ size = 30, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
