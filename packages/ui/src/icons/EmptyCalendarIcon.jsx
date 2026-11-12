// Lucide: Calendar. Preserve the existing display size and inherited state colors.
import { Calendar as LucideIcon } from 'lucide-react';

export default function EmptyCalendarIcon({ size = 36, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
